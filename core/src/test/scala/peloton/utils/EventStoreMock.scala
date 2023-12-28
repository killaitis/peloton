package peloton.utils

import peloton.*
import peloton.persistence.EventStore
import peloton.persistence.PersistenceId
import peloton.persistence.EncodedEvent

import cats.effect.IO
import fs2.Stream
import scala.collection.mutable

/**
  * A simple in-memory implemention of the [[EventStore]] that provides additional
  * functionality for testing and debugging
  */
class EventStoreMock extends EventStore:

  private val encodedEvents = mutable.Map.empty[PersistenceId, Vector[EncodedEvent]]

  override def create(): IO[Unit] = IO.unit

  override def drop(): IO[Unit] = IO.unit

  override def clear(): IO[Unit] = IO.pure(encodedEvents.clear())

  override def purge(persistenceId: PersistenceId, snapshotsToKeep: Int): IO[Unit] = IO.raiseError(new NotImplementedError)

  override def readEncodedEvents(persistenceId: PersistenceId, startFromLatestSnapshot: Boolean): Stream[IO, EncodedEvent] = 
    if startFromLatestSnapshot then
      val events = encodedEvents.getOrElse(persistenceId, Vector.empty[EncodedEvent])
      val indexOfLastSnapshot = events.lastIndexWhere(_.isSnapshot)
      val eventsSinceLastSnapshot = 
        if indexOfLastSnapshot > 0 
        then events.drop(indexOfLastSnapshot)
        else events
      Stream.emits(eventsSinceLastSnapshot)
    else 
      Stream.emits(encodedEvents.getOrElse(persistenceId, Vector.empty[EncodedEvent]))

  override def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit] =
    IO {
      val currentEvents = encodedEvents.getOrElse(persistenceId, Vector.empty[EncodedEvent])
      encodedEvents.put(persistenceId, currentEvents :+ encodedEvent)
    }.void
