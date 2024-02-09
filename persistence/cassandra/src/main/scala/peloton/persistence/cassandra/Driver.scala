package peloton.persistence.cassandra

import peloton.config.Config
import peloton.persistence.DurableStateStore
import peloton.persistence.EventStore

import cats.effect.*
import cats.effect.std.AtomicCell


import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement

import scala.jdk.CollectionConverters.*
import java.net.InetSocketAddress

// https://github.com/apache/cassandra-java-driver
// https://cassandra.apache.org/doc/stable/cassandra/cql/index.html

class Driver extends peloton.persistence.Driver:

  override def createDurableStateStore(config: Config.DurableStateStore): IO[Resource[IO, DurableStateStore]] =
    for
      cqlSession         <- createCqlSession(config.params)
      durableStateStore   = cqlSession.map(DurableStateStoreCassandra(_))
    yield durableStateStore

  override def createEventStore(config: Config.EventStore): IO[Resource[IO, EventStore]] =
    for
      cqlSession     <- createCqlSession(config.params)
      statementCache <- AtomicCell[IO].of(Map.empty[String, PreparedStatement])
      eventStore      = cqlSession.map(EventStoreCassandra(_, statementCache))
    yield eventStore

  private def createCqlSession(params: Map[String, String]): IO[Resource[IO, CqlSession]] = 
    def getParameter(key: String): IO[String] = 
      IO.fromOption(params.get(key))(IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))

    def getOptionalParameter(key: String, defaultValue: String): String = 
      params.get(key).getOrElse(defaultValue)

    for 
      username       <- getParameter("user")
      password       <- getParameter("password")
      datacenter     <- getParameter("datacenter")
      contactPoints   = getOptionalParameter("contact-points", "")
                        .split(",").toList
                        .map(_.split(":").toList)
                        .collect { case host :: port :: Nil => InetSocketAddress(host, port.toInt) }
                        .asJava
    yield 
      Resource.fromAutoCloseable(IO(
        CqlSession
            .builder()
            .withLocalDatacenter(datacenter)
            .withAuthCredentials(username, password)
            .addContactPoints(contactPoints)
            .build()
        ))

end Driver
