package peloton.utils

import cats.effect.*
import com.typesafe.config.ConfigFactory

trait SystemPropertiesSpec:
  def withSystemProperties[F[_], A](properties: Map[String, String])(f: => IO[A]): IO[A] =
    def acquire: IO[Unit] = 
      IO:
        properties.foreach((k, v) => System.setProperty(k, v))
        ConfigFactory.invalidateCaches()

    def release(_u: Unit): IO[Unit] = 
      IO:
        properties.keys.foreach(System.clearProperty)
        ConfigFactory.invalidateCaches()

    Resource.make(acquire)(release).use(_ => f)
