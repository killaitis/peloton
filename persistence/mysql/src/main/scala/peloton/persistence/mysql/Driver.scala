package peloton.persistence.mysql

import peloton.config.Config
import peloton.persistence.DurableStateStore
import peloton.persistence.EventStore

import cats.effect.*

import doobie.util.transactor.Transactor
import doobie.hikari.HikariTransactor

import com.zaxxer.hikari.HikariConfig

import scala.util.Try


class Driver extends peloton.persistence.Driver:

  override def createDurableStateStore(config: Config.DurableStateStore): IO[Resource[IO, DurableStateStore]] =
    for
      hikariConfig       <- getHikariConfig(config.params)
      durableStateStore   = createMySQLDurableStateStore(hikariConfig)
    yield durableStateStore

  override def createEventStore(config: Config.EventStore): IO[Resource[IO, EventStore]] = 
    for
      hikariConfig <- getHikariConfig(config.params)
      eventStore    = createMySQLEventStore(hikariConfig)
    yield eventStore

  private def createMySQLDurableStateStore(hikariConfig: HikariConfig): Resource[IO, DurableStateStore] =
    for
      given Transactor[IO] <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
      durableStateStore     = new DurableStateStoreMySQL
    yield durableStateStore

  private def createMySQLEventStore(hikariConfig: HikariConfig): Resource[IO, EventStore] =
    for
      given Transactor[IO] <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
      eventStore            = new EventStoreMySQL
    yield eventStore

  private def getHikariConfig(params: Map[String, String]): IO[HikariConfig] =
    def getParameter(key: String): IO[String] = 
      IO.fromOption(params.get(key))(IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))

    def getOptionalParameter(key: String, defaultValue: String): String = 
      params.get(key).getOrElse(defaultValue)

    for
      jdbcUrl          <- getParameter("url")
      user             <- getParameter("user")
      password         <- getParameter("password")
      maximumPoolSize  <- IO.fromTry(Try(getOptionalParameter("maximum-pool-size", "10").toInt))
      hikariConfig      = {
                            val hikariConfig = new HikariConfig()
                            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver")
                            hikariConfig.setJdbcUrl(jdbcUrl)
                            hikariConfig.setUsername(user)
                            hikariConfig.setPassword(password)
                            hikariConfig.setMaximumPoolSize(maximumPoolSize)
                            hikariConfig.setAutoCommit(false)
                            hikariConfig
                          }

    yield hikariConfig
  end getHikariConfig

end Driver
