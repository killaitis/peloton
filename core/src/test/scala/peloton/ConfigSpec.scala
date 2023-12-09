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
    config.Config.default().asserting: config =>
      config shouldBe Config(
                        Peloton(
                          None,
                          Persistence(
                            Postgresql(
                              url             = "jdbc:postgresql://localhost:5432/peloton",
                              user            = "peloton",
                              password        = "peloton",
                              maximumPoolSize = 10
                            )
                          )
                        )
                      )

  it should "read the config from a given String" in:
    val configStr = 
          """
            |peloton {
            |  persistence {
            |    store {
            |      type              = postgresql
            |      url               = "jdbc:postgresql://mydb.com:5432/peloton"
            |      user              = "alvin"
            |      password          = "stardust"
            |      maximum-pool-size = 42
            |    }
            |  }
            |}
          """.stripMargin
          
    config.Config.string(configStr).asserting: config =>
      config shouldBe Config(
                        Peloton(
                          None,
                          Persistence(
                            Postgresql(
                              url             = "jdbc:postgresql://mydb.com:5432/peloton",
                              user            = "alvin",
                              password        = "stardust",
                              maximumPoolSize = 42
                            )
                          )
                        )
                      )

  it should "merge Java properties into the config" in:
    val properties = Map(
      "peloton.persistence.store.url"      -> "jdbc:postgresql://mydb.com:5432/peloton",
      "peloton.persistence.store.user"     -> "alvin",
      "peloton.persistence.store.password" -> "stardust"
    )

    val configStr = 
          """
            |peloton {
            |  persistence {
            |    store {
            |      type              = postgresql
            |      maximum-pool-size = 42
            |    }
            |  }
            |}
          """.stripMargin

    withSystemProperties(properties):
      config.Config.string(configStr).asserting: config =>
        config shouldBe Config(
                          Peloton(
                            None, 
                            Persistence(
                              Postgresql(
                                url             = "jdbc:postgresql://mydb.com:5432/peloton",
                                user            = "alvin",
                                password        = "stardust",
                                maximumPoolSize = 42
                              )
                            )
                          )
                        )

end ConfigSpec
