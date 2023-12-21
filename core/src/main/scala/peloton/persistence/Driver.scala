package peloton.persistence

import peloton.config.Config

import cats.effect.{IO, Resource}

/**
  * A driver is able to create an instance of a [[DurableStateStore]] as a `Resource`.
  */
trait Driver:
  def createDurableStateStore(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]]
  def createEventStore(persistenceConfig: Config.Persistence): IO[Resource[IO, EventStore]]
