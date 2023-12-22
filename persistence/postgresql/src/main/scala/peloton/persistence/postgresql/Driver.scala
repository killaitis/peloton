package peloton.persistence.postgresql

import peloton.config.Config
import peloton.config.Config.Persistence
import peloton.persistence.DurableStateStore
import peloton.persistence.EventStore

import cats.effect.*

import doobie.util.transactor.Transactor
import doobie.hikari.HikariTransactor

import com.zaxxer.hikari.HikariConfig

import scala.util.Try


class Driver extends peloton.persistence.Driver:

  override def createDurableStateStore(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]] =
    for
      hikariConfig       <- getHikariConfig(using persistenceConfig)
      durableStateStore   = createPostgreSQLDurableStateStore(hikariConfig)
    yield durableStateStore

  override def createEventStore(persistenceConfig: Persistence): IO[Resource[IO, EventStore]] = 
    for
      hikariConfig <- getHikariConfig(using persistenceConfig)
      eventStore    = createPostgreSQLEventStore(hikariConfig)
    yield eventStore

  private def createPostgreSQLDurableStateStore(hikariConfig: HikariConfig): Resource[IO, DurableStateStore] =
    for
      given Transactor[IO] <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
      durableStateStore     = new DurableStateStorePostgreSQL
    yield durableStateStore

  private def createPostgreSQLEventStore(hikariConfig: HikariConfig): Resource[IO, EventStore] =
    for
      given Transactor[IO] <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
      eventStore            = new EventStorePostgreSQL
    yield eventStore

  private def getHikariConfig(using persistenceConfig: Config.Persistence): IO[HikariConfig] =
    for
      jdbcUrl          <- getParameter("url")
      user             <- getParameter("user")
      password         <- getParameter("password")
      maximumPoolSize  <- IO.fromTry(Try(getOptionalParameter("maximum-pool-size", "10").toInt))
      hikariConfig      = {
                            val hikariConfig = new HikariConfig()
                            hikariConfig.setDriverClassName("org.postgresql.Driver")
                            hikariConfig.setJdbcUrl(jdbcUrl)
                            hikariConfig.setUsername(user)
                            hikariConfig.setPassword(password)
                            hikariConfig.setMaximumPoolSize(maximumPoolSize)
                            hikariConfig.setAutoCommit(false)
                            hikariConfig
                          }

    yield hikariConfig
  end getHikariConfig

  def getParameter(key: String)(using persistenceConfig: Config.Persistence): IO[String] = 
    IO.fromOption(persistenceConfig.params.get(key))(IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))

  def getOptionalParameter(key: String, defaultValue: String)(using persistenceConfig: Config.Persistence): String = 
    persistenceConfig.params.get(key).getOrElse(defaultValue)

end Driver
