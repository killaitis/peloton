package peloton.persistence.cassandra

import peloton.persistence.{EncodedEvent, EventStore, PersistenceId}

import cats.effect.IO
import cats.effect.std.AtomicCell
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*
import com.datastax.oss.driver.api.core.uuid.Uuids
import fs2.Stream

import java.util.UUID


private [cassandra] class EventStoreCassandra(cqlSession: CqlSession, 
                                              statementCache: AtomicCell[IO, Map[String, PreparedStatement]],
                                              replicationStrategy: String,
                                              replicationFactor: Int
                                             ) extends EventStore:

  override def create(): IO[Unit] = 
    execute(s"""
              create keyspace if not exists peloton 
              with replication = { 
                'class': '$replicationStrategy', 
                'replication_factor' : $replicationFactor
              }
            """) >>
    execute("""
              create table if not exists peloton.eventstore (
                persistence_id  text,
                sequence_id     timeuuid,
                is_snapshot     boolean,
                timestamp       bigint,
                payload         blob,

                primary key (persistence_id, sequence_id)
              ) with clustering order by (sequence_id asc)
            """) >>
    execute("""
              create table if not exists peloton.snapshots (
                persistence_id  text,
                sequence_id     timeuuid,

                primary key (persistence_id, sequence_id)
              ) with clustering order by (sequence_id desc)
            """)

  override def drop(): IO[Unit] =
    execute("drop table if exists peloton.eventstore") >>
    execute("drop table if exists peloton.snapshots")

  override def clear(): IO[Unit] =
    execute("truncate table peloton.eventstore") >>
    execute("truncate table peloton.snapshots")

  private def readEventsSinceSnapshot(persistenceId: PersistenceId, snapshot: String): Stream[IO, EncodedEvent] =
    query("""
        select payload, timestamp, is_snapshot
        from peloton.eventstore 
        where persistence_id = ? and sequence_id >= ? 
        order by sequence_id
      """, persistenceId.toString(), UUID.fromString(snapshot)
    )
    .map: row => 
      EncodedEvent(payload    = row.getBytesUnsafe("payload").array(),
                   timestamp  = row.getLong("timestamp"),
                   isSnapshot = row.getBoolean("is_snapshot")
                  )

  private def readAllEvents(persistenceId: PersistenceId): Stream[IO, EncodedEvent] =
    query("""
           select payload, timestamp, is_snapshot
           from peloton.eventstore 
           where persistence_id = ?
           order by sequence_id
       """, persistenceId.toString()
    )
      .map: row => 
        EncodedEvent(payload    = row.getBytesUnsafe("payload").array(),
                     timestamp  = row.getLong("timestamp"),
                     isSnapshot = row.getBoolean("is_snapshot")
                    )



  override def readEncodedEvents(persistenceId: PersistenceId, 
                                 startFromLatestSnapshot: Boolean
                                ): Stream[IO, EncodedEvent] =
    if startFromLatestSnapshot 
    then 
      Stream
        .eval(getCurrentSnapshot(persistenceId))
        .flatMap: currentSnapshot => 
          currentSnapshot match 
            case None           => readAllEvents(persistenceId)
            case Some(snapshot) => readEventsSinceSnapshot(persistenceId, snapshot)
    else 
      readAllEvents(persistenceId)

    

  override def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit] =
    val sequenceId = Uuids.timeBased()
    if encodedEvent.isSnapshot 
    then 
      execute("""
        insert into peloton.eventstore (
          persistence_id,
          sequence_id,
          timestamp,
          payload,
          is_snapshot
        ) values (?, ?, ?, ?, ?)
        """, 
        persistenceId.toString(), sequenceId, encodedEvent.timestamp, java.nio.ByteBuffer.wrap(encodedEvent.payload), true
      ) >>
      execute("""
        insert into peloton.snapshots (
          persistence_id,
          sequence_id
        ) values (?, ?)
        """, 
        persistenceId.toString(), sequenceId
      )
    else 
      execute("""
        insert into peloton.eventstore (
          persistence_id,
          sequence_id,
          timestamp,
          payload,
          is_snapshot
        ) values (?, ?, ?, ?, ?)
        """, 
        persistenceId.toString(), sequenceId, encodedEvent.timestamp, java.nio.ByteBuffer.wrap(encodedEvent.payload), false
      )

  override def purge(persistenceId: PersistenceId, snapshotsToKeep: Int): IO[Unit] = 
    if snapshotsToKeep < 0 then
      IO.raiseError(IllegalArgumentException("snapshotsToKeep must not be negative"))
    else if snapshotsToKeep == 0 then
      query("""
        select sequence_id
        from peloton.snapshots
        where persistence_id = ?
      """, persistenceId.toString())
        .evalMap: row =>
          val snapshotId = row.getUuid("sequence_id")
          execute("""
            delete from peloton.eventstore
            where persistence_id = ? and sequence_id = ?
          """, persistenceId.toString(), snapshotId) >>
          execute("""
            delete from peloton.snapshots
            where persistence_id = ? and sequence_id = ?
          """, persistenceId.toString(), snapshotId)
        .compile
        .drain
    else
      for
        maybeOldestSnapshot  <- getOldestSnapshot(persistenceId, snapshotsToKeep)
        _                    <- maybeOldestSnapshot match
                                  case None =>
                                    IO.unit
                                  case Some(snapshot) =>
                                    execute("""
                                      delete from peloton.eventstore
                                      where persistence_id = ? and sequence_id < ?
                                      """, persistenceId.toString(), UUID.fromString(snapshot)) >>
                                    execute("""
                                      delete from peloton.snapshots
                                      where persistence_id = ? and sequence_id < ?
                                      """, persistenceId.toString(), UUID.fromString(snapshot))
      yield ()

  
  private def getCurrentSnapshot(persistenceId: PersistenceId): IO[Option[String]] = 
    getOldestSnapshot(persistenceId, 1)
                    
  private def getOldestSnapshot(persistenceId: PersistenceId, snapshotsToKeep: Int): IO[Option[String]] = 
    query(
      """
        select sequence_id
        from peloton.snapshots
        where persistence_id = ?
        order by sequence_id desc
        limit ?
      """, persistenceId.toString(), snapshotsToKeep
    )
      .map { row => row.getUuid("sequence_id").toString() }
      .compile
      .last

  private def prepare(statement: String): Stream[IO, PreparedStatement] = 
    Stream.eval:
      statementCache.evalModify: cache =>
        cache.get(statement) match
          case None => 
            IO.blocking(cqlSession.prepare(statement)).map: preparedStatement =>
              (cache + (statement -> preparedStatement), preparedStatement)
          case Some(preparedStatement) => 
            IO.pure((cache, preparedStatement))

  private def execute(statement: String, args: Any*): IO[Unit] = 
    prepare(statement)
      .evalMap(preparedStatement => IO(preparedStatement.bind(args*)))
      .evalMap(boundStatement => IO.blocking(cqlSession.execute(boundStatement)))
      .compile
      .drain

  private def query(statement: String, args: Any*): Stream[IO, Row] =
    prepare(statement)
      .evalMap(preparedStatement => IO(preparedStatement.bind(args*)))
      .flatMap: boundStatement =>
        // Adapt the driver's Reactive Streams publisher to the JDK Flow API expected by FS2.
        Stream.fromPublisher[IO](
          org.reactivestreams.FlowAdapters.toFlowPublisher(cqlSession.executeReactive(boundStatement)),
          512
        )

end EventStoreCassandra
