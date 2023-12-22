package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgreSQLSpec:

  lazy val testContainerConfig: Config =
    val imageName = DockerImageName.parse("postgres").withTag("14.5")
    val container = new PostgreSQLContainer(imageName)
    
    container.start()
    
    val dbHost = container.getHost()
    val dbPort = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
    val dbName = container.getDatabaseName()
    val dbUsername = container.getUsername()
    val dbPassword = container.getPassword()
    val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"

    Config(
      Peloton(
        persistence = Some(Persistence(
          driver = "peloton.persistence.postgresql.Driver", 
          params = Map(
            "url"      -> jdbcUrl,
            "user"     -> dbUsername,
            "password" -> dbPassword
          )
        ))
      )
    )
