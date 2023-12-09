package peloton.config

import cats.effect.*

import pureconfig.*
import pureconfig.generic.derivation.default.*

import Config.* 

case class Config(
  peloton: Peloton
) derives ConfigReader


object Config:

  case class Peloton(
    http: Option[Http],
    persistence: Persistence
  ) derives ConfigReader

  case class Http(
    hostname: String,
    port: Int
  ) derives ConfigReader
  
  case class Persistence(
    store: Store
  ) derives ConfigReader

  sealed trait Store derives ConfigReader
  case class Postgresql(
    url: String,
    user: String,
    password: String,
    maximumPoolSize: Int
  ) extends Store

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
