package peloton

import cats.effect.*
import doobie.util.transactor.Transactor
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import peloton.persistence.postgresql.DurableStateStorePostgreSQL
import peloton.persistence.DurableStateStore

class DurableStateStorePostgreSQLSpec extends DurableStateStoreSpec: 
  val store = DurableStateStorePostgreSQLSpec.store

object DurableStateStorePostgreSQLSpec:    
  private lazy val store: DurableStateStore =
    val imageName = DockerImageName.parse("postgres").withTag("14.5")
    val container = new PostgreSQLContainer(imageName)
    
    container.start()
    
    val dbHost = container.getHost()
    val dbPort = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
    val dbName = container.getDatabaseName()
    val dbUsername = container.getUsername()
    val dbPassword = container.getPassword()
    val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"

    given Transactor[IO] = Transactor.fromDriverManager[IO](
      driver      = "org.postgresql.Driver",
      url         = jdbcUrl,
      user        = dbUsername,
      password    = dbPassword,
      logHandler  = None
    )

    new DurableStateStorePostgreSQL
