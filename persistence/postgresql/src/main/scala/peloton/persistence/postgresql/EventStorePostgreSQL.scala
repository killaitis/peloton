package peloton.persistence.postgresql

import peloton.persistence.EventStore
import peloton.persistence.PersistenceId
import peloton.persistence.EncodedEvent

import cats.effect.IO
import fs2.Stream

import doobie.util.transactor.Transactor
import doobie.*
import doobie.implicits.*

private [postgresql] class EventStorePostgreSQL(using xa: Transactor[IO]) extends EventStore:

  def create(): IO[Unit] = 
    (
      for 
        _  <- sql"""
                create table if not exists event_store (
                  persistence_id  varchar(255)  not null,
                  payload         bytea         not null,
                  timestamp       bigint        not null,

                  primary key (persistence_id, timestamp)
                )
              """.update.run
      yield ()
    ).transact(xa)

  def drop(): IO[Unit] =
    sql"drop table if exists event_store"
      .update.run.transact(xa).void

  def clear(): IO[Unit] =
    sql"truncate table event_store"
      .update.run.transact(xa).void

  def readEncodedEvents(persistenceId: PersistenceId): Stream[IO, EncodedEvent] =
    sql"select payload, timestamp from event_store where persistence_id = ${persistenceId.toString()} order by timestamp"
      .query[EncodedEvent].stream.transact(xa)

  def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit] =
    sql"""
      insert into event_store (
        persistence_id,
        payload,
        timestamp
      ) values (
        ${persistenceId.toString()},
        ${encodedEvent.payload},
        ${encodedEvent.timestamp}
      )
    """.update.run.transact(xa).void

end EventStorePostgreSQL
