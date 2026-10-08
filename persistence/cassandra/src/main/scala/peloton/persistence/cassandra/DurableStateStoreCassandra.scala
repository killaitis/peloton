package peloton.persistence.cassandra

import peloton.persistence.*
import peloton.persistence.DurableStateStore.*

import cats.effect.IO

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet

private [cassandra] class DurableStateStoreCassandra(
  cqlSession: CqlSession,
  replicationStrategy: String,
  replicationFactor: Int
) extends DurableStateStore:

  private lazy val selectRow = cqlSession.prepare("select payload, revision, timestamp from peloton.durable_state where persistence_id = ?")
  private lazy val selectRevision = cqlSession.prepare("select revision from peloton.durable_state where persistence_id = ?")
  private lazy val insertRow = cqlSession.prepare("""
                insert into peloton.durable_state (
                  persistence_id,
                  payload,
                  revision,
                  timestamp
                ) values (?, ?, ?, ?) if not exists
                """)
  private lazy val updateRow = cqlSession.prepare("""
                update 
                  peloton.durable_state 
                set 
                  revision=?, 
                  timestamp=?,
                  payload=?
                where 
                  persistence_id=?
                if revision=?
                """)

  override def create(): IO[Unit] = 
    for 
      _  <- IO.blocking(cqlSession.execute(s"create keyspace if not exists peloton with replication = {'class': '$replicationStrategy', 'replication_factor' : $replicationFactor}"))
      _  <- IO.blocking(cqlSession.execute(
                """
                create table if not exists peloton.durable_state (
                  persistence_id  varchar,
                  revision        bigint,
                  payload         blob,
                  timestamp       bigint,

                  primary key (persistence_id)
                )
                """))
    yield ()

  override def drop(): IO[Unit] = 
    IO.blocking(cqlSession.execute("drop table if exists peloton.durable_state")).void

  override def clear(): IO[Unit] = 
    IO.blocking(cqlSession.execute("truncate table peloton.durable_state")).void

  override def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]] =
    IO.blocking:
      val result = cqlSession.execute(selectRow.bind(persistenceId.toString()))

      Option(result.one())
        .map: row => 
          val payload = row.getBytesUnsafe("payload").array()
          val revision = row.getLong("revision")
          val timestamp = row.getLong("timestamp")

          EncodedState(payload = payload, revision = revision , timestamp = timestamp)


  override def writeEncodedState(persistenceId: PersistenceId, state: EncodedState): IO[Unit] =
    (for
      maybeCurrentRevision <- readRevision(persistenceId)
      currentRevision       = maybeCurrentRevision.getOrElse(0L)
      expectedRevision      = currentRevision + 1L
      _                    <- 
                              if state.revision == expectedRevision then
                                maybeCurrentRevision match
                                  case None    => insertEncodedState(persistenceId, state).flatMap(checkApplied(persistenceId, state))
                                  case Some(revision) => updateEncodedState(persistenceId, revision, state).flatMap(checkApplied(persistenceId, state))
                              else 
                                IO.raiseError(RevisionMismatchError(persistenceId = persistenceId,
                                                                    expectedRevision = expectedRevision,
                                                                    actualRevision = state.revision
                                                                   )
                                             )
    yield ())

  private def checkApplied(persistenceId: PersistenceId, state: EncodedState)(result: ResultSet): IO[Unit] =
    if result.wasApplied() then IO.unit
    else
      readRevision(persistenceId).flatMap: maybeCurrentRevision =>
        val expectedRevision = maybeCurrentRevision.fold(1L)(_ + 1L)
        IO.raiseError(RevisionMismatchError(persistenceId, expectedRevision, state.revision))

  private def readRevision(persistenceId: PersistenceId): IO[Option[Long]] = 
    IO.blocking:
      val result = cqlSession.execute(selectRevision.bind(persistenceId.toString()))

      Option(result.one()).map(_.getLong("revision"))

  private def insertEncodedState(persistenceId: PersistenceId, state: EncodedState): IO[ResultSet] =
    IO.blocking:
      cqlSession.execute(insertRow.bind(persistenceId.toString(),
                                        java.nio.ByteBuffer.wrap(state.payload),
                                        state.revision,
                                        state.timestamp
                                       )
                        )

  private def updateEncodedState(persistenceId: PersistenceId, currentRevision: Long, state: EncodedState): IO[ResultSet] =
    IO.blocking:
      cqlSession.execute(updateRow.bind(state.revision,
                                        state.timestamp,
                                        java.nio.ByteBuffer.wrap(state.payload),
                                        persistenceId.toString(),
                                        currentRevision
                                       )
                        )
