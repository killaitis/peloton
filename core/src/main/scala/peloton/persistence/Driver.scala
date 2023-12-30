package peloton.persistence

import peloton.config.Config

import cats.effect.{IO, Resource}
import scala.util.Try

/**
  * A driver is able to create an instances of [[DurableStateStore]] or [[EventStore]] as a `Resource`.
  */
trait Driver:
  def createDurableStateStore(config: Config.DurableStateStore): IO[Resource[IO, DurableStateStore]]
  def createEventStore(config: Config.EventStore): IO[Resource[IO, EventStore]]

object Driver:
  private [persistence] def apply(driverName: String): IO[Driver] =
    IO.fromTry(Try {
      val classLoader = this.getClass().getClassLoader()
      val driverClass = classLoader.loadClass(driverName)
      val ctor        = driverClass.getConstructor()
      val driver      = ctor.newInstance().asInstanceOf[Driver]
      driver
    })
