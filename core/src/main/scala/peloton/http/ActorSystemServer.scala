package peloton.http

import cats.effect.IO
import cats.effect.Resource

import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.server.{Router, Server}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

import io.circe.generic.auto.*
import io.circe.Decoder
import io.circe.Encoder

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.scala.ScalaKryoSerializer
import com.comcast.ip4s.{Hostname, Port}

import scala.concurrent.duration.*
import scala.util.Try
import java.util.Base64

import peloton.actor.ActorSystem
import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.http.Codecs.given

object ActorSystemServer:
  object Http:
    case class TellRequest(actorName: String, payload: String)
    case class AskRequest(actorName: String, payload: String, timeout: FiniteDuration)

    case class TellResponse()
    case class AskResponse(payload: String)
    case class InvalidRequest(error: String)
    
  def apply(host: Hostname, port: Port, actorSystem: ActorSystem): Resource[IO, Server] = 

    // And out of the door goes type safety...
    // This is veeery ugly, but we have to somehow "convince" the Actor API that the actor supports our generic message
    given CanAsk[Any, Any] = canAsk[Any, Any]

    val actorRestService: HttpRoutes[IO] =
      HttpRoutes.of[IO]:
        case req @ POST -> Root / "tell" => 
          (for
            tellRequest  <- req.as[Http.TellRequest]
            message      <- deserializePayload(tellRequest.payload)
            actorRef     <- actorSystem.actorRef[Any](tellRequest.actorName)
            _            <- actorRef.tell(message)
            httpResponse <- Ok(Http.TellResponse())
          yield httpResponse)
            .handleErrorWith(err => BadRequest(Http.InvalidRequest(err.getMessage)))

        case req @ POST -> Root / "ask" =>
          (for
            askRequest   <- req.as[Http.AskRequest]
            message      <- deserializePayload(askRequest.payload)
            // ct            = scala.reflect.ClassTag[Any](message.getClass)
            actorRef     <- actorSystem.actorRef[Any](askRequest.actorName) // (using ct)
            response     <- actorRef.ask(message = message, timeout = askRequest.timeout)
            payload      <- serializePayload(response)
            httpResponse <- Ok(Http.AskResponse(payload))
          yield httpResponse)
            .handleErrorWith(err => BadRequest(Http.InvalidRequest(err.getMessage)))
      .map(_.putHeaders("Access-Control-Allow-Origin" -> "*"))

    val httpApp = Router(
      "/" -> actorRestService,
    ).orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
  end apply

  private lazy val config = ConfigFactory.defaultApplication.withFallback(ConfigFactory.defaultReference)
  private lazy val serializer = ScalaKryoSerializer(config, getClass.getClassLoader)
  private lazy val encoder = Base64.getEncoder
  private lazy val decoder = Base64.getDecoder

  private [peloton] def deserializePayload(payload: String): IO[Any] = 
    for
      decoded      <- IO.fromTry(Try(decoder.decode(payload)))
      message      <- IO.fromTry(serializer.deserialize[Any](decoded))
    yield message

  private [peloton] def serializePayload(payload: Any): IO[String] =
    for
      buffer  <- IO.fromTry(serializer.serialize(payload))
      encoded <- IO.fromTry(Try(encoder.encodeToString(buffer)))
    yield encoded

end ActorSystemServer