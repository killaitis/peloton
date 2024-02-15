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
      cqlSession           <- createCqlSession(config.params)
      statementCache       <- AtomicCell[IO].of(Map.empty[String, PreparedStatement])
      replicationStrategy   = getOptionalParameter(config.params, "replication-strategy", "SimpleStrategy")
      replicationFactor     = getOptionalParameter(config.params, "replication-factor", "1").toIntOption.getOrElse(1)
      eventStore            = cqlSession.map: session => 
                                EventStoreCassandra(
                                  cqlSession          = session, 
                                  statementCache      = statementCache,
                                  replicationStrategy = replicationStrategy,
                                  replicationFactor   = replicationFactor
                                )            
    yield eventStore

  private def getParameter(params: Map[String, String], key: String): IO[String] = 
    IO.fromOption(params.get(key))(IllegalArgumentException(s"Invalid persistence config: key '$key' is missing"))

  private def getOptionalParameter(params: Map[String, String], key: String, defaultValue: String): String = 
    params.get(key).getOrElse(defaultValue)

  private def createCqlSession(params: Map[String, String]): IO[Resource[IO, CqlSession]] = 
    for 
      username       <- getParameter(params, "user")
      password       <- getParameter(params, "password")
      datacenter     <- getParameter(params, "datacenter")
      contactPoints   = getOptionalParameter(params, "contact-points", "")
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
