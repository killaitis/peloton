package peloton.persistence.cassandra

import peloton.persistence.*
import peloton.persistence.DurableStateStore.*

import cats.effect.IO

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*

private [cassandra] class DurableStateStoreCassandra(cqlSession: CqlSession) extends DurableStateStore:

  private lazy val selectRow = cqlSession.prepare("select payload, revision, timestamp from peloton.durable_state where persistence_id = ?")
  private lazy val selectRevision = cqlSession.prepare("select revision from peloton.durable_state where persistence_id = ?")
  private lazy val insertRow = cqlSession.prepare("""
                insert into peloton.durable_state (
                  persistence_id,
                  payload,
                  revision,
                  timestamp
                ) values (?, ?, ?, ?)
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
                """)

  override def create(): IO[Unit] = 
    for 
      _  <- IO(cqlSession.execute("create keyspace if not exists peloton with replication = {'class': 'SimpleStrategy', 'replication_factor' : 1}"))
      _  <- IO(cqlSession.execute(
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
    IO(cqlSession.execute("drop table if exists peloton.durable_state")).void

  override def clear(): IO[Unit] = 
    IO(cqlSession.execute("truncate table peloton.durable_state")).void

  override def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]] =
    IO:
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
                                  case None    => insertEncodedState(persistenceId, state)
                                  case Some(_) => updateEncodedState(persistenceId, state)
                              else 
                                IO.raiseError(RevisionMismatchError(persistenceId = persistenceId,
                                                                    expectedRevision = expectedRevision,
                                                                    actualRevision = state.revision
                                                                   )
                                             )
    yield ())

  private def readRevision(persistenceId: PersistenceId): IO[Option[Long]] = 
    IO:
      val result = cqlSession.execute(selectRevision.bind(persistenceId.toString()))

      Option(result.one()).map(_.getLong("revision"))

  private def insertEncodedState(persistenceId: PersistenceId, state: EncodedState): IO[Unit] = 
    IO:
      cqlSession.execute(insertRow.bind(persistenceId.toString(),
                                        java.nio.ByteBuffer.wrap(state.payload),
                                        state.revision,
                                        state.timestamp
                                       )
                        )
      ()

  private def updateEncodedState(persistenceId: PersistenceId, state: EncodedState): IO[Unit] = 
    IO:
      cqlSession.execute(updateRow.bind(state.revision,
                                        state.timestamp,
                                        java.nio.ByteBuffer.wrap(state.payload),
                                        persistenceId.toString()
                                       )
                        )
      ()
