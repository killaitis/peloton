package peloton.config

import cats.effect.*

// TODO: Waiting for improved Scala 3 support: https://github.com/pureconfig/pureconfig/pull/1599

import pureconfig.*
import pureconfig.generic.derivation.default.*

import Config.* 

final case class Config(
  peloton: Peloton = Peloton()
) derives ConfigReader


object Config:

  final case class Peloton(
    http: Option[Http] = None,
    persistence: Persistence = Persistence()
  ) derives ConfigReader

  final case class Http(
    hostname: String,
    port: Int
  ) derives ConfigReader
  
  final case class Persistence(
    durableStateStore: Option[DurableStateStore] = None,
    eventStore: Option[EventStore] = None,
  ) derives ConfigReader

  final case class DurableStateStore(
    driver: String,
    params: Map[String, String] = Map.empty
  ) derives ConfigReader

  final case class EventStore(
    driver: String,
    params: Map[String, String] = Map.empty
  ) derives ConfigReader

  def default(): IO[Config] =
    IO: 
      ConfigSource
        .default
        .loadOrThrow[Config]
  
  def file(configFilePath: String): IO[Config] =
    IO:
      ConfigSource
        .defaultOverrides
        .withFallback(ConfigSource.file(configFilePath))
        .loadOrThrow[Config]

  def string(configStr: String): IO[Config] =
    IO:
      ConfigSource
        .defaultOverrides
        .withFallback(ConfigSource.string(configStr))
        .loadOrThrow[Config]
