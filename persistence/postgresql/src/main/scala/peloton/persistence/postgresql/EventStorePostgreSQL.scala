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


  override def create(): IO[Unit] = 
    (
      for 
        _  <- sql"create schema if not exists peloton".update.run

        _  <- sql"""
                create table if not exists peloton.event_store (
                  persistence_id  varchar(255)  not null,
                  sequence_id     bigint        not null,
                  timestamp       bigint        not null,
                  payload         bytea         not null,

                  primary key (persistence_id, sequence_id)
                )
              """.update.run

        _  <- sql"create sequence if not exists peloton.event_store_seq".update.run
      yield ()
    ).transact(xa)

  override def drop(): IO[Unit] =
    (
      for
        _  <- sql"drop table if exists peloton.event_store".update.run
        _  <- sql"drop sequence if exists peloton.event_store_seq".update.run
      yield ()
    ).transact(xa).void

  override def clear(): IO[Unit] =
    sql"truncate table peloton.event_store"
      .update.run.transact(xa).void

  override def readEncodedEvents(persistenceId: PersistenceId): Stream[IO, EncodedEvent] =
    sql"""
      select 
        payload, 
        timestamp 
      from peloton.event_store 
      where persistence_id = ${persistenceId.toString()} 
      order by sequence_id
    """.query[EncodedEvent].stream.transact(xa)

  override def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit] =
    sql"""
      insert into peloton.event_store (
        persistence_id,
        sequence_id,
        timestamp,
        payload
      ) values (
        ${persistenceId.toString()},
        nextval('peloton.event_store_seq'),
        ${encodedEvent.timestamp},
        ${encodedEvent.payload}
      )
    """.update.run.transact(xa).void

end EventStorePostgreSQL
