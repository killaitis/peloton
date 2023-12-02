package peloton.persistence

import cats.effect.* 

import doobie.util.transactor.Transactor
import doobie.hikari.HikariTransactor
import com.zaxxer.hikari.HikariConfig

import peloton.persistence.postgresql.DurableStateStorePostgreSQL
import peloton.config.Config

object DurableStateStoreFactory:

  def create(config: Config): Resource[IO, DurableStateStore] =
    config.peloton.persistence.store match
      case config: Config.Postgresql => createPostgreSQLStore(config)

  private def createPostgreSQLStore(config: Config.Postgresql): Resource[IO, DurableStateStore] =
    for
      hikariConfig       <- Resource.pure:
                              // For the full list of Hikari configuration options see https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby
                              val hikariConfig = new HikariConfig()
                              hikariConfig.setDriverClassName("org.postgresql.Driver")
                              hikariConfig.setJdbcUrl(config.url)
                              hikariConfig.setUsername(config.user)
                              hikariConfig.setPassword(config.password)
                              hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
                              hikariConfig.setAutoCommit(false)
                              hikariConfig

      given Transactor[IO] <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
      store                 = new postgresql.DurableStateStorePostgreSQL
    yield store
