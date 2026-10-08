package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.utility.DockerImageName

object CassandraSpec:

  lazy val testContainerConfig: Config = configFor("4.1")
  lazy val currentTestContainerConfig: Config = configFor("5.0")

  private def configFor(version: String): Config = {
    val container = new CassandraContainer(DockerImageName.parse("cassandra").withTag(version))
    container.start()
    val contactPoint = container.getContactPoint()
    val datacenter = container.getLocalDatacenter()
    val dbUsername = container.getUsername()
    val dbPassword = container.getPassword()

    val params = Map(
      "contact-points"  -> s"${contactPoint.getHostString()}:${contactPoint.getPort()}",
      "datacenter"      -> datacenter,
      "user"            -> dbUsername,
      "password"        -> dbPassword
    )

    Config(
      Peloton(
        persistence = Persistence(
          eventStore = Some(EventStore(
            driver = "peloton.persistence.cassandra.Driver", 
            params = params
          )),
          durableStateStore = Some(DurableStateStore(
            driver = "peloton.persistence.cassandra.Driver", 
            params = params
          ))
        )
      )
    )
  }

end CassandraSpec
