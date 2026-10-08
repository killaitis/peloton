package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgreSQLSpec:

  lazy val testContainerConfig: Config = configFor("15")
  lazy val currentTestContainerConfig: Config = configFor("18")

  private def configFor(version: String): Config = {
    val imageName = DockerImageName.parse("postgres").withTag(version)
    val container = new PostgreSQLContainer(imageName)
    container.start()
    val dbUsername = container.getUsername()
    val dbPassword = container.getPassword()
    val jdbcUrl = container.getJdbcUrl()

    Config(
      Peloton(
        persistence = Persistence(
          eventStore = Some(EventStore(
            driver = "peloton.persistence.postgresql.Driver", 
            params = Map(
              "url"      -> jdbcUrl,
              "user"     -> dbUsername,
              "password" -> dbPassword
            )
          )),
          durableStateStore = Some(DurableStateStore(
            driver = "peloton.persistence.postgresql.Driver", 
            params = Map(
              "url"      -> jdbcUrl,
              "user"     -> dbUsername,
              "password" -> dbPassword
            )
          ))
        )
      )
    )
  }

end PostgreSQLSpec
