package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgreSQLSpec:

  private lazy val container = {
    val imageName = DockerImageName.parse("postgres").withTag("14.5")
    val container = new PostgreSQLContainer(imageName)
    
    container.start()
    container
  }

  lazy val testContainerConfig: Config =
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

end PostgreSQLSpec
