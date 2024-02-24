package peloton.persistence.mysql

import peloton.persistence.EventStore
import peloton.persistence.PersistenceId
import peloton.persistence.EncodedEvent

import cats.effect.IO
import fs2.Stream

import doobie.util.transactor.Transactor
import doobie.*
import doobie.implicits.*

private [mysql] class EventStoreMySQL(using xa: Transactor[IO]) extends EventStore:


  override def create(): IO[Unit] = 
    (
      for 
        _  <- sql"create schema if not exists peloton".update.run

        _  <- sql"""
                create table if not exists peloton.event_store (
                  sequence_id     bigint        auto_increment not null,
                  persistence_id  varchar(255)  not null,
                  timestamp       bigint        not null,
                  is_snapshot     boolean       not null default false,
                  payload         blob          not null,

                  primary key (sequence_id, persistence_id)
                ) engine InnoDB
              """.update.run
      yield ()
    ).transact(xa)

  override def drop(): IO[Unit] =
    (
      for
        _  <- sql"drop table if exists peloton.event_store".update.run
      yield ()
    ).transact(xa).void

  override def clear(): IO[Unit] =
    sql"truncate table peloton.event_store"
      .update.run.transact(xa).void

  override def readEncodedEvents(persistenceId: PersistenceId, 
                                 startFromLatestSnapshot: Boolean
                                ): Stream[IO, EncodedEvent] =
    if startFromLatestSnapshot then 
      sql"""
        select
          payload, 
          timestamp,
          is_snapshot
        from peloton.event_store 
        where 
              persistence_id = ${persistenceId.toString()} 
          and sequence_id >= (select coalesce(max(sequence_id), 0)
                              from peloton.event_store 
                              where 
                                persistence_id = ${persistenceId.toString()} 
                                and is_snapshot = true
                            ) 
        order by sequence_id
      """.query[EncodedEvent].stream.transact(xa)
    else 
      sql"""
        select
          payload, 
          timestamp,
          is_snapshot
        from peloton.event_store 
        where persistence_id = ${persistenceId.toString()} 
        order by sequence_id
      """.query[EncodedEvent].stream.transact(xa)

  override def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit] =
    sql"""
      insert into peloton.event_store (
        persistence_id,
        timestamp,
        payload,
        is_snapshot
      ) values (
        ${persistenceId.toString()},
        ${encodedEvent.timestamp},
        ${encodedEvent.payload},
        ${encodedEvent.isSnapshot}
      )
    """.update.run.transact(xa).void

  override def purge(persistenceId: PersistenceId, snapshotsToKeep: Int): IO[Unit] = 
    sql"""
      delete from peloton.event_store 
      where 
            persistence_id = ${persistenceId.toString()} 
        and sequence_id < ( select min(sequence_id) 
                            from 
                              ( select sequence_id 
                                from peloton.event_store 
                                where 
                                      persistence_id = ${persistenceId.toString()} 
                                  and is_snapshot 
                                order by sequence_id desc
                                limit ${snapshotsToKeep}
                              ) as A
                          )
    """.update.run.transact(xa).void

end EventStoreMySQL
