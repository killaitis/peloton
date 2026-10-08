package peloton.persistence.mysql

import peloton.persistence.*
import peloton.persistence.DurableStateStore.*

import cats.effect.IO

import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*

private [mysql] class DurableStateStoreMySQL(using xa: Transactor[IO]) extends DurableStateStore:

  override def create(): IO[Unit] = 
    (
      for 
        _  <- sql"create schema if not exists peloton".update.run

        _  <- sql"""
                create table if not exists peloton.durable_state (
                  persistence_id  varchar(255)  not null,
                  revision        bigint        not null,
                  payload         longblob      not null,
                  timestamp       bigint        not null,

                      primary key (persistence_id)
                ) engine InnoDB
              """.update.run

        _  <- sql"alter table peloton.durable_state modify payload longblob not null".update.run
        _  <- dropRedundantRevisionIndex
      yield ()
    ).transact(xa)

  private def dropRedundantRevisionIndex: ConnectionIO[Unit] =
    sql"""
      select count(*)
      from information_schema.statistics
      where table_schema = 'peloton'
        and table_name = 'durable_state'
        and index_name = 'persistence_id'
        and seq_in_index = 2
        and column_name = 'revision'
    """.query[Int].unique.flatMap:
      case 0 => FC.unit
      case _ =>
        sql"alter table peloton.durable_state drop index persistence_id"
          .update.run.map(_ => ())

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
                                  case Some(currentRevision) =>
                                    updateEncodedState(persistenceId, currentRevision, state).flatMap: rowsUpdated =>
                                      if rowsUpdated == 1 then FC.unit
                                      else FC.raiseError(RevisionMismatchError(persistenceId, expectedRevision, state.revision))
                              else 
                                FC.raiseError(RevisionMismatchError(persistenceId = persistenceId,
                                                                    expectedRevision = expectedRevision,
                                                                    actualRevision = state.revision
                                                                   )
                                             )
    yield ()).transact(xa)

  private def readRevision(persistenceId: PersistenceId): ConnectionIO[Option[Long]] = 
    sql"select revision from peloton.durable_state where persistence_id = ${persistenceId.toString()} for update"
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

  private def updateEncodedState(persistenceId: PersistenceId, currentRevision: Long, state: EncodedState): ConnectionIO[Int] =
    sql"""
      update 
        peloton.durable_state 
      set 
        revision=${state.revision}, 
        timestamp=${state.timestamp},
        payload=${state.payload}
      where 
        persistence_id=${persistenceId.toString()} and revision=$currentRevision
    """.update.run

end DurableStateStoreMySQL
