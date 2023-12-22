package peloton.benchmarks

import peloton.actor.ActorSystem
import peloton.persistence.*
import peloton.utils.*

import peloton.actors.FooActor
import peloton.actors.BarActor
import peloton.PostgreSQLSpec

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.syntax.*
import scala.io.AnsiColor.*
import java.time.Duration
import org.scalatest.Tag


object Benchmark extends Tag("Benchmark")

class PersistentActorBenchmark
  extends AsyncFlatSpec 
    with AsyncIOSpec 
    with Matchers:

  private given SelfAwareStructuredLogger[IO] = Slf4jFactory.create[IO].getLogger

  val config = PostgreSQLSpec.testContainerConfig

  behavior of "The DurableStateStore factory"

  it should "handle parallel load with many messages" taggedAs(Benchmark) in:
    load(numActors = 10, numMessages = 10_000)

  it should "handle parallel load with many actors and messages" taggedAs(Benchmark) in:
    load(numActors = 1_000, numMessages = 100)

  it should "handle parallel load with many actors" taggedAs(Benchmark) in:
    load(numActors = 10_000, numMessages = 10)

    
  private def load(numActors: Int, numMessages: Int)(using clock: Clock[IO]): IO[Unit] = 
    for
      _      <- info"### Performing load test with $numActors actors and $numMessages messages per actor"
      
      _      <- ActorSystem.use(config) { _ ?=> 
                  DurableStateStore.use(config) { store ?=>
                    for
                      _          <- info"Preparing store ..."
                      _          <- store.create()
                      _          <- store.clear()
                      
                      // Spawn a bunch of Foo and Bar actors in parallel to stress the system
                      _          <- info"Spawning actors ..."
                      fooActors  <- (0 until numActors)
                                      .parTraverse(i => FooActor.spawn(s"foo-$i", PersistenceId.of(s"foo-$i")))
                                      .map(_.toVector)

                      barActors  <- (0 until numActors)
                                      .parTraverse(i => BarActor.spawn(s"bar-$i", PersistenceId.of(s"bar-$i")))
                                      .map(_.toVector)

                      // Send a bunch of messsages to each actor in parallel 
                      _          <- info"Sending messages ..."
                      t1         <- clock.realTimeInstant
                      _          <- (0 until numActors)
                                      .parTraverse_ { i =>
                                        val fooActor = fooActors(i)
                                        val barActor = barActors(i)
                                        
                                        (0 until numMessages)
                                          .traverse_ { j => 
                                            for
                                              _ <- fooActor ! FooActor.Set(x = j, y = 2*j)
                                              _ <- barActor ! BarActor.Set(s = s"x_$j")
                                            yield ()
                                          }
                                      }
                      t2         <- clock.realTimeInstant
                      _          <- info"  => Done! d=${Duration.between(t1, t2)}"

                      // Wait for the messages to be processed and check the actor states
                      _          <- info"Waiting for completion ..."
                      _          <- warn"${GREEN}*** THIS MIGHT TAKE A COUPLE OF MINUTES TO COMPLETE. PICK A CUP OF TEA AND BE PATIENT! ***${RESET}"
                      _          <- (0 until numActors)
                                      .parTraverse_(i => 
                                        for 
                                          _  <- fooActors(i) ? FooActor.Get() asserting {
                                                  _ shouldBe FooActor.GetResponse(x = numMessages - 1, y = 2*(numMessages - 1))
                                                }
                                          _  <- barActors(i) ? BarActor.Get() asserting {
                                                  _ shouldBe BarActor.GetResponse(s = s"x_${numMessages - 1}")
                                                }
                                        yield ()
                                      )
                      t3         <- clock.realTimeInstant
                      _          <- info"  => Done! d=${Duration.between(t2, t3)}"

                      // Shut down the actors
                      _          <- info"Shutting down ..."
                      _          <- (0 until numActors)
                                      .parTraverse_(i => 
                                        fooActors(i).terminate >> 
                                        barActors(i).terminate
                                      )
                    yield ()
                  }
                }
    yield ()

end PersistentActorBenchmark

/*
object PersistentActorBenchmark:

  private lazy val postgresContainer: PostgreSQLContainer[Nothing] =
    val imageName = DockerImageName.parse("postgres").withTag("14.5")
    val container = new PostgreSQLContainer(imageName)
    container.start()
    container
    
  def getConfigForDockerizedPostgres() =
    val dbHost = postgresContainer.getHost()
    val dbPort = postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
    val dbName = postgresContainer.getDatabaseName()
    val dbUsername = postgresContainer.getUsername()
    val dbPassword = postgresContainer.getPassword()
    val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
    getConfig(jdbcUrl = jdbcUrl, dbUsername = dbUsername, dbPassword = dbPassword)

  def getConfig(jdbcUrl: String, dbUsername: String, dbPassword: String) =
    config.Config.string(
      s"""
        |peloton {
        |  persistence {
        |    driver = peloton.persistence.postgresql.Driver
        |    params {
        |      url               = "$jdbcUrl"
        |      user              = "$dbUsername"
        |      password          = "$dbPassword"
        |      maximum-pool-size = 10
        |    }
        |  }
        |}
      """.stripMargin
    )
  end getConfig

end DurableStateStoreFactorySpec
*/