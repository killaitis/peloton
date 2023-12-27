package peloton.persistence.postgresql

import peloton.persistence.*
import peloton.persistence.DurableStateStore.*

import cats.effect.IO
import doobie.*
import doobie.implicits.*

private [postgresql] class DurableStateStorePostgreSQL(using xa: Transactor[IO]) extends DurableStateStore:

  override def create(): IO[Unit] = 
    (
      for 
        _  <- sql"create schema if not exists peloton".update.run

        _  <- sql"""
                create table if not exists peloton.durable_state (
                  persistence_id  varchar(255)  not null,
                  revision        bigint        not null,
                  payload         bytea         not null,
                  timestamp       bigint        not null,

                  primary key (persistence_id)
                )
              """.update.run

              // for readRevision()
        _  <- sql"""
                create unique index if not exists idx_durable_state_revision on peloton.durable_state (persistence_id, revision)
              """.update.run
      yield ()
    ).transact(xa)

  override def drop(): IO[Unit] = 
    sql"drop table if exists peloton.durable_state"
      .update.run.transact(xa).void

  override def clear(): IO[Unit] = 
    sql"truncate table peloton.durable_state"
      .update.run.transact(xa).void

  override def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]] = 
    sql"select payload, revision, timestamp from peloton.durable_state where persistence_id = ${persistenceId.toString()}"
      .query[EncodedState].option.transact(xa)

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
                                FC.raiseError(RevisionMismatchError(persistenceId = persistenceId,
                                                                    expectedRevision = expectedRevision,
                                                                    actualRevision = state.revision
                                                                   )
                                             )
    yield ())
    .transact(xa)

  private def readRevision(persistenceId: PersistenceId): ConnectionIO[Option[Long]] = 
    sql"select revision from peloton.durable_state where persistence_id = ${persistenceId.toString()}"
      .query[Long].option

  private def insertEncodedState(persistenceId: PersistenceId, state: EncodedState): ConnectionIO[Int] = 
    sql"""
      insert into peloton.durable_state (
        persistence_id,
        payload,
        revision,
        timestamp
      ) values (
        ${persistenceId.toString()},
        ${state.payload},
        ${state.revision},
        ${state.timestamp}
      )
    """.update.run

  private def updateEncodedState(persistenceId: PersistenceId, state: EncodedState): ConnectionIO[Int] = 
    sql"""
      update 
        peloton.durable_state 
      set 
        revision=${state.revision}, 
        timestamp=${state.timestamp},
        payload=${state.payload}
      where 
        persistence_id=${persistenceId.toString()}
    """.update.run
