package peloton.utils

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

private [peloton] object Kryo:
  lazy val config = ConfigFactory.defaultApplication.withFallback(ConfigFactory.defaultReference)
  lazy val serializer = ScalaKryoSerializer(config, getClass.getClassLoader)
