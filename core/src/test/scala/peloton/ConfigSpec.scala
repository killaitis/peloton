package peloton

import peloton.config.Config.*
import peloton.utils.SystemPropertiesSpec

import cats.effect.*

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import cats.effect.testing.scalatest.AsyncIOSpec
import config.Config


class ConfigSpec 
    extends AsyncFlatSpec 
      with AsyncIOSpec
      with SystemPropertiesSpec 
      with Matchers:

  behavior of "A Peloton Config"
  
  it should "read the config from the default application config file" in:
    config.Config.default[IO]().asserting: config =>
      config shouldBe Config()

  it should "read the config from a given String" in:
    val configStr = 
          """
            |peloton {
            |  persistence {
            |    durable-state-store {
            |      driver = peloton.persistence.postgresql.Driver
            |      params {
            |        url               = "jdbc:postgresql://mydb.com:5432/peloton"
            |        user              = "alvin"
            |        password          = "stardust"
            |        maximum-pool-size = 42
            |      }
            |    },
            |    event-store {
            |      driver = peloton.persistence.phantasydb.Driver
            |      params {
            |        url      = "jdbc:phantasy://localhost:4711/peloton"
            |        user     = "peterpan"
            |        password = "unicorn"
            |      }
            |    }
            |  }
            |}
          """.stripMargin
          
    config.Config.string[IO](configStr).asserting: config =>
      config shouldBe Config(
                        Peloton(
                          None,
                          Persistence(
                            durableStateStore = Some(DurableStateStore(
                              driver = "peloton.persistence.postgresql.Driver",
                              params = Map(
                                "url"               -> "jdbc:postgresql://mydb.com:5432/peloton",
                                "user"              -> "alvin",
                                "password"          -> "stardust",
                                "maximum-pool-size" -> "42"
                              )
                            )),
                            eventStore = Some(EventStore(
                              driver = "peloton.persistence.phantasydb.Driver",
                              params = Map(
                                "url"               -> "jdbc:phantasy://localhost:4711/peloton",
                                "user"              -> "peterpan",
                                "password"          -> "unicorn"
                              )
                            ))
                          )
                        )
                      )

  it should "be able to reuse config blocks as aliases" in:
    val configStr = 
          """
            |peloton {
            |  persistence {
            |    durable-state-store = ${my-postgresql-config}
            |    event-store         = ${my-postgresql-config}
            |  }
            |}
            |
            |my-postgresql-config {
            |  driver = peloton.persistence.postgresql.Driver
            |  params {
            |    url               = "jdbc:postgresql://mydb.com:5432/peloton"
            |    user              = "alvin"
            |    password          = "stardust"
            |    maximum-pool-size = 42
            |  }
            |}
          """.stripMargin
          
    config.Config.string[IO](configStr).asserting: config =>
      config shouldBe Config(
                        Peloton(
                          None,
                          Persistence(
                            durableStateStore = Some(DurableStateStore(
                              driver = "peloton.persistence.postgresql.Driver",
                              params = Map(
                                "url"               -> "jdbc:postgresql://mydb.com:5432/peloton",
                                "user"              -> "alvin",
                                "password"          -> "stardust",
                                "maximum-pool-size" -> "42"
                              )
                            )),
                            eventStore = Some(EventStore(
                              driver = "peloton.persistence.postgresql.Driver",
                              params = Map(
                                "url"               -> "jdbc:postgresql://mydb.com:5432/peloton",
                                "user"              -> "alvin",
                                "password"          -> "stardust",
                                "maximum-pool-size" -> "42"
                              )
                            ))
                          )
                        )
                      )

  it should "merge Java properties into the config" in:
    val properties = Map(
      "peloton.persistence.event-store.params.url"      -> "jdbc:postgresql://mydb.com:5432/peloton",
      "peloton.persistence.event-store.params.user"     -> "alvin",
      "peloton.persistence.event-store.params.password" -> "stardust"
    )

    val configStr = 
          """
            |peloton {
            |  persistence {
            |    event-store {
            |      driver = peloton.persistence.postgresql.Driver
            |      params {  
            |        maximum-pool-size = 42
            |      }
            |    }
            |  }
            |}
          """.stripMargin

    withSystemProperties(properties):
      config.Config.string[IO](configStr).asserting: config =>
        config shouldBe Config(
                          Peloton(
                            None, 
                            Persistence(
                              eventStore = Some(EventStore(
                                driver = "peloton.persistence.postgresql.Driver",
                                params = Map(
                                  "url"               -> "jdbc:postgresql://mydb.com:5432/peloton",
                                  "user"              -> "alvin",
                                  "password"          -> "stardust",
                                  "maximum-pool-size" -> "42"
                                )
                              ))
                            )
                          )
                        )

end ConfigSpec
