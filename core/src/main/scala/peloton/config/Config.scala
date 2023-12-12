package peloton.config

import cats.effect.*

import pureconfig.*
import pureconfig.generic.derivation.default.*

import Config.* 

case class Config(
  peloton: Peloton = Peloton()
) derives ConfigReader


object Config:

  case class Peloton(
    http: Option[Http] = None,
    persistence: Option[Persistence] = None
  ) derives ConfigReader

  case class Http(
    hostname: String,
    port: Int
  ) derives ConfigReader
  
  case class Persistence(
    driver: String,
    params: Map[String, String]
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
