package peloton.http

import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorSystem
import peloton.actor.ActorRef
import peloton.http.Codecs.given
import peloton.utils.Kryo

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

import com.comcast.ip4s.{Hostname, Port}

import scala.concurrent.duration.*
import scala.util.Try
import java.util.Base64

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

    def checkMessageType[A, M](actorRef: ActorRef[A], message: M): IO[Unit] =
      val actorClass = actorRef.classTag.runtimeClass
      if message == null then
        if actorClass.isPrimitive then IO.raiseError(InvalidRemoteRequest("null is not valid for a primitive actor message type"))
        else IO.unit
      else
        val messageClass = message.getClass
        if ActorSystem.isAssignable(actorClass, messageClass) then IO.unit
        else IO.raiseError(InvalidRemoteRequest(s"Invalid message type: expected ${actorClass.getName}, received ${messageClass.getName}"))

    val corsHeaders =
      org.http4s.Headers(
        "Access-Control-Allow-Origin" -> "*",
        "Access-Control-Allow-Methods" -> "POST, OPTIONS",
        "Access-Control-Allow-Headers" -> "Content-Type, Accept",
        "Access-Control-Max-Age" -> "600"
      )

    val actorRestService: HttpRoutes[IO] =
      HttpRoutes.of[IO]:
        case OPTIONS -> Root / "tell" => Ok()
        case OPTIONS -> Root / "ask"  => Ok()

        case req @ POST -> Root / "tell" => 
          (for
            tellRequest  <- req.as[Http.TellRequest].adaptError { case err => InvalidRemoteRequest(err.getMessage) }
            message      <- deserializePayload(tellRequest.payload).adaptError { case err => InvalidRemoteRequest(err.getMessage) }
            actorRef     <- actorSystem.actorRef[Any](tellRequest.actorName).adaptError {
                              case _: NoSuchElementException => RemoteActorNotFound(tellRequest.actorName)
                            }
            _            <- checkMessageType(actorRef, message)
            _            <- actorRef.tell(message)
            httpResponse <- Ok(Http.TellResponse())
          yield httpResponse)
            .handleErrorWith(handleError)

        case req @ POST -> Root / "ask" =>
          (for
            askRequest   <- req.as[Http.AskRequest].adaptError { case err => InvalidRemoteRequest(err.getMessage) }
            message      <- deserializePayload(askRequest.payload).adaptError { case err => InvalidRemoteRequest(err.getMessage) }
            actorRef     <- actorSystem.actorRef[Any](askRequest.actorName).adaptError {
                              case _: NoSuchElementException => RemoteActorNotFound(askRequest.actorName)
                            }
            _            <- checkMessageType(actorRef, message)
            response     <- actorRef.ask(message = message, timeout = askRequest.timeout)
            payload      <- serializePayload(response)
            httpResponse <- Ok(Http.AskResponse(payload))
          yield httpResponse)
            .handleErrorWith(handleError)

    val httpApp = Router(
      "/" -> actorRestService,
    ).orNotFound.map(_.putHeaders(corsHeaders))

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
  end apply

  private lazy val encoder = Base64.getEncoder
  private lazy val decoder = Base64.getDecoder

  private final case class InvalidRemoteRequest(reason: String) extends RuntimeException(reason)
  private final case class RemoteActorNotFound(actorName: String) extends RuntimeException(s"actor not found: $actorName")

  private def handleError(error: Throwable): IO[Response[IO]] = error match
    case requestError: InvalidRemoteRequest => BadRequest(Http.InvalidRequest(requestError.getMessage))
    case actorNotFound: RemoteActorNotFound => NotFound(Http.InvalidRequest(actorNotFound.getMessage))
    case _: java.util.concurrent.TimeoutException | _: scala.concurrent.TimeoutException =>
      GatewayTimeout(Http.InvalidRequest("actor request timed out"))
    case _ => InternalServerError(Http.InvalidRequest("actor request failed"))

  private [peloton] def deserializePayload(payload: String): IO[Any] = 
    for
      decoded      <- IO.fromTry(Try(decoder.decode(payload)))
      message      <- IO.fromTry(Kryo.serializer.deserialize[Any](decoded))
    yield message

  private [peloton] def serializePayload(payload: Any): IO[String] =
    for
      buffer  <- IO.fromTry(Kryo.serializer.serialize(payload))
      encoded <- IO.fromTry(Try(encoder.encodeToString(buffer)))
    yield encoded

end ActorSystemServer