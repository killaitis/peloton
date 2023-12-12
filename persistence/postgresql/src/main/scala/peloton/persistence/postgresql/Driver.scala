package peloton.persistence.postgresql

import peloton.config.Config
import cats.effect.*
import peloton.persistence.DurableStateStore

import doobie.util.transactor.Transactor
import doobie.hikari.HikariTransactor
import com.zaxxer.hikari.HikariConfig


class Driver extends peloton.persistence.Driver:

  case class PostgresqlConfig(
    url: String,
    user: String,
    password: String,
    maximumPoolSize: Int
  )

  override def create(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]] =

    def getParameter(key: String): IO[String] = 
      IO.fromOption(persistenceConfig.params.get(key))(new java.lang.IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))
    def getOptionalParameter(key: String, defaultValue: String): String = 
      persistenceConfig.params.get(key).getOrElse(defaultValue)

    for
      jdbcUrl          <- getParameter("url")
      user             <- getParameter("user")
      password         <- getParameter("password")
      maximumPoolSize   = getOptionalParameter("maximum-pool-size", "10")
      config            = PostgresqlConfig(
                            url             = jdbcUrl,
                            user            = user,
                            password        = password,
                            maximumPoolSize = maximumPoolSize.toInt
                          )
      store = createPostgreSQLStore(config)
    yield store
  end create

  private def createPostgreSQLStore(config: PostgresqlConfig): Resource[IO, DurableStateStore] =
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
      store                 = new DurableStateStorePostgreSQL
    yield store
