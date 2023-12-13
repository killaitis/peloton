package peloton.utils

import peloton.*

import cats.effect.IO
import scala.collection.mutable
import peloton.persistence.DurableStateStore
import peloton.persistence.EncodedState
import peloton.persistence.PersistenceId

/**
  * A simple in-memory implemention of the [[DurableStateStore]] that provides additional
  * functionality for testing and debugging
  */
class DurableStateStoreMock extends DurableStateStore:

  private val encodedStates = mutable.Map.empty[PersistenceId, EncodedState]

  override def create(): IO[Unit] = IO.unit

  override def drop(): IO[Unit] = IO.unit

  override def clear(): IO[Unit] = IO.pure(encodedStates.clear())

  override def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]] =
    IO.pure(encodedStates.get(persistenceId))

  override def writeEncodedState(persistenceId: PersistenceId, encodedState: EncodedState): IO[Unit] = 
    IO.pure(encodedStates.put(persistenceId, encodedState)).void
