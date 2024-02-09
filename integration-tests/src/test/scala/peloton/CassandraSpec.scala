package peloton

import peloton.config.Config
import peloton.config.Config.*

import org.testcontainers.containers.CassandraContainer
import org.testcontainers.utility.DockerImageName

object CassandraSpec:

  private lazy val container = {
    val container = new CassandraContainer(DockerImageName.parse("cassandra").withTag("4"))
    
    container.start()
    container
  }

  lazy val testContainerConfig: Config =
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
