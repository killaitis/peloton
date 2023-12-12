package peloton.persistence

import peloton.config.Config

import cats.effect.{IO, Resource}

trait Driver:
  def create(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]]
