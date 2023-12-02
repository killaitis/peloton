package peloton.persistence.postgresql

import peloton.persistence.*
import peloton.persistence.DurableStateStore.*

import cats.effect.IO
import doobie.*
import doobie.implicits.*

class DurableStateStorePostgreSQL(using xa: Transactor[IO])
  extends DurableStateStore:

  override def create(): IO[Unit] = 
    (
      for 
        _  <- sql"""
                create table if not exists durable_state_io (
                  persistence_id  varchar(255)  not null,
                  revision        bigint        not null,
                  payload         bytea         not null,
                  timestamp       bigint        not null,

                  primary key (persistence_id)
                )
              """.update.run

              // for readRevision()
        _  <- sql"""
                create unique index if not exists idx_durable_state_io_revision on durable_state_io (persistence_id, revision)
              """.update.run
      yield ()
    ).transact(xa)

  override def drop(): IO[Unit] = 
    sql"drop table if exists durable_state_io"
      .update.run.transact(xa).void

  override def clear(): IO[Unit] = 
    sql"truncate table durable_state_io"
      .update.run.transact(xa).void

  override def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]] = 
    sql"select payload, revision, timestamp from durable_state_io where persistence_id = ${persistenceId.toString()}"
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
    sql"select revision from durable_state_io where persistence_id = ${persistenceId.toString()}"
      .query[Long].option

  private def insertEncodedState(persistenceId: PersistenceId, state: EncodedState): ConnectionIO[Int] = 
    sql"""
      insert into durable_state_io (
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
        durable_state_io 
      set 
        revision=${state.revision}, 
        timestamp=${state.timestamp},
        payload=${state.payload}
      where 
        persistence_id=${persistenceId.toString()}
    """.update.run
