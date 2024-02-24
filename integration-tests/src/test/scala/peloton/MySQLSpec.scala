package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

object MySQLSpec:

  private lazy val container = {
    val imageName = DockerImageName.parse("mysql").withTag("5.7") // ("8") does not work
    val container = new MySQLContainer(imageName)
    
    container.start()
    container
  }

  lazy val testContainerConfig: Config =
    val dbUsername = "root" // TODO: use container.getUsername() and grant access to test user
    val dbPassword = container.getPassword()
    val jdbcUrl = container.getJdbcUrl()

    println(s"dbUsername [$dbUsername]")
    println(s"dbPassword [$dbPassword]")
    println(s"jdbcUrl [$jdbcUrl]")

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

end MySQLSpec
