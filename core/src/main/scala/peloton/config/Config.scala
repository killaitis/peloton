package peloton.config

import cats.effect.Sync

import pureconfig.{ConfigSource, ConfigReader}
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.catseffect.syntax.*

import Config.* 

final case class Config(
  peloton: Peloton = Peloton()
)


object Config:

  final case class Peloton(
    http: Option[Http] = None,
    persistence: Persistence = Persistence()
  )

  final case class Http(
    hostname: String,
    port: Int
  )
  
  final case class Persistence(
    durableStateStore: Option[DurableStateStore] = None,
    eventStore: Option[EventStore] = None,
  )

  final case class DurableStateStore(
    driver: String,
    params: Map[String, String] = Map.empty
  )

  final case class EventStore(
    driver: String,
    params: Map[String, String] = Map.empty
  )

  private given ConfigReader[Http]              = deriveReader
  private given ConfigReader[DurableStateStore] = deriveReader
  private given ConfigReader[EventStore]        = deriveReader
  private given ConfigReader[Persistence]       = deriveReader
  private given ConfigReader[Peloton]           = deriveReader
  private given ConfigReader[Config]            = deriveReader

  def default[F[_]: Sync](): F[Config] =
    ConfigSource
      .default
      .loadF[F, Config]()
  
  def file[F[_]: Sync](configFilePath: String): F[Config] =
    ConfigSource
      .defaultOverrides
      .withFallback(ConfigSource.file(configFilePath))
      .loadF[F, Config]()

  def string[F[_]: Sync](configStr: String): F[Config] =
    ConfigSource
      .defaultOverrides
      .withFallback(ConfigSource.string(configStr))
      .loadF[F, Config]()
