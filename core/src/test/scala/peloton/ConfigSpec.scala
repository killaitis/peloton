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
                          None
                        )
                      )

  it should "read the config from a given String" in:
    val configStr = 
          """
            |peloton {
            |  persistence {
            |    driver = peloton.persistence.postgresql.Driver
            |    params {
            |      url               = "jdbc:postgresql://mydb.com:5432/peloton"
            |      user              = "alvin"
            |      password          = "stardust"
            |      maximum-pool-size = 42
            |    }
            |  }
            |}
          """.stripMargin
          
/* 
A Peloton Config should read the config from a given String - 

  Config(Peloton(None, Some(Persistence("peloton.persistence.postgresql.Driver", 
    Map(
      "maximum-pool-size" -> "42", 
      "password" -> "stardust", 
      "user" -> "alvin", 
      "url" -> "jdbc:postgresql://mydb.com:5432/peloton"
    ))))) 
  
  was not equal to 

  Config(Peloton(None, Some(Persistence("peloton.persistence.postgresql.Driver", 
    Map(
      "url" -> "jdbc:postgresql://mydb.com:5432/peloton", 
      "user" -> "alvin", 
      "password" -> "stardust", 
      "maximumPoolSize" -> "42"
    )))))          


*/
    config.Config.string(configStr).asserting: config =>
      config shouldBe Config(
                        Peloton(
                          None,
                          Some(Persistence(
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

  it should "merge Java properties into the config" in:
    val properties = Map(
      "peloton.persistence.driver"          -> "peloton.persistence.postgresql.Driver",
      "peloton.persistence.params.url"      -> "jdbc:postgresql://mydb.com:5432/peloton",
      "peloton.persistence.params.user"     -> "alvin",
      "peloton.persistence.params.password" -> "stardust"
    )

    val configStr = 
          """
            |peloton {
            |  persistence {
            |    params {  
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
                            Some(Persistence(
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

end ConfigSpec
