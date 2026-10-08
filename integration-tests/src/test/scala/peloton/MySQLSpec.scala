package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

object MySQLSpec:

  lazy val testContainerConfig: Config = configFor("8.4")
  lazy val currentTestContainerConfig: Config = configFor("9.7")

  private def configFor(version: String): Config = {
    val imageName = DockerImageName.parse("mysql").withTag(version)
    val container = new MySQLContainer(imageName)
    container.start()
    val dbUsername = "root" // TODO: use container.getUsername() and grant access to test user
    val dbPassword = container.getPassword()
    val jdbcUrl = container.getJdbcUrl()

    Config(
      Peloton(
        persistence = Persistence(
          eventStore = Some(EventStore(
            driver = "peloton.persistence.mysql.Driver", 
            params = Map(
              "url"      -> jdbcUrl,
              "user"     -> dbUsername,
              "password" -> dbPassword
            )
          )),
          durableStateStore = Some(DurableStateStore(
            driver = "peloton.persistence.mysql.Driver", 
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

end MySQLSpec
