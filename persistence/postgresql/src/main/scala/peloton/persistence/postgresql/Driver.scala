package peloton.persistence.postgresql

import peloton.config.Config
import cats.effect.*
import peloton.persistence.DurableStateStore

import doobie.util.transactor.Transactor
import doobie.hikari.HikariTransactor
import com.zaxxer.hikari.HikariConfig
import peloton.persistence.EventStore
import peloton.config.Config.Persistence


class Driver extends peloton.persistence.Driver:

  override def createDurableStateStore(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]] =
    for
      hikariConfig       <- getHikariConfig(persistenceConfig)
      durableStateStore   = createPostgreSQLDurableStateStore(hikariConfig)
    yield durableStateStore

  override def createEventStore(persistenceConfig: Persistence): IO[Resource[IO, EventStore]] = 
    for
      hikariConfig <- getHikariConfig(persistenceConfig)
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

  private def getHikariConfig(persistenceConfig: Config.Persistence): IO[HikariConfig] =

    def getParameter(key: String): IO[String] = 
      IO.fromOption(persistenceConfig.params.get(key))(new java.lang.IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))
    def getOptionalParameter(key: String, defaultValue: String): String = 
      persistenceConfig.params.get(key).getOrElse(defaultValue)

    for
      jdbcUrl          <- getParameter("url")
      user             <- getParameter("user")
      password         <- getParameter("password")
      maximumPoolSize   = getOptionalParameter("maximum-pool-size", "10")
      hikariConfig      = {
                            val hikariConfig = new HikariConfig()
                            hikariConfig.setDriverClassName("org.postgresql.Driver")
                            hikariConfig.setJdbcUrl(jdbcUrl)
                            hikariConfig.setUsername(user)
                            hikariConfig.setPassword(password)
                            hikariConfig.setMaximumPoolSize(maximumPoolSize.toInt)
                            hikariConfig.setAutoCommit(false)
                            hikariConfig
                          }

    yield hikariConfig
  end getHikariConfig

end Driver
