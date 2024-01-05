package peloton.http

import peloton.actor.Actor.CanAsk
import peloton.actor.ActorRef

import cats.effect.IO

import org.http4s.ember.client.EmberClientBuilder
import org.http4s.Headers
import org.http4s.headers.Accept
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.Method.*
import org.http4s.Request
import org.http4s.Uri
import org.http4s.Uri.Host
import org.http4s.Uri.Authority
import org.http4s.Uri.Scheme
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

import io.circe.generic.auto.*

import scala.concurrent.duration.*
import scala.reflect.ClassTag

class RemoteActorRef[M](host: String, port: Int, actorName: String)(using ct: reflect.ClassTag[M]) extends ActorRef[M]:

  import RemoteActorRef.*
  import ActorSystemServer.Http.*
  import ActorSystemServer.*
  import Codecs.given

  private val baseUri = Uri(scheme = Some(Scheme.http), authority = Some(Authority(host = Host.unsafeFromString(host), port = Some(port))))

  override def name: String = actorName
  override def classTag: ClassTag[M] = ct
  override def tell(message: M): IO[Unit] = 
    for
      payload  <- serializePayload(message)
      url       = baseUri.withPath(Root /  "tell")
      request   = Request[IO](method = POST, uri = url, headers = headers)
                    .withEntity(TellRequest(actorName = actorName, 
                                            payload = payload
                                           ))
                    .withContentType(`Content-Type`(MediaType.application.json))
      _        <- httpClient.use(_.expect[TellResponse](request))
    yield ()

  override def ask[M2 <: M, R](message: M2, timeout: FiniteDuration)(using CanAsk[M2, R]): IO[R] = 
    for
      payload  <- serializePayload(message)
      url       = baseUri.withPath(Root /  "ask")
      request   = Request[IO](method = POST, uri = url, headers = headers)
                    .withEntity(AskRequest(actorName = actorName, 
                                           payload = payload, 
                                           timeout = timeout
                                          ))
                    .withContentType(`Content-Type`(MediaType.application.json))
      response <- httpClient.use(_.expect[AskResponse](request))
      response <- deserializePayload(response.payload)
    yield response.asInstanceOf[R]

  override def terminate: IO[Unit] = IO.raiseError(new RuntimeException("Terminating a remote actor is not allowed"))

end RemoteActorRef

object RemoteActorRef:

  private val httpClient = 
    EmberClientBuilder
      .default[IO]
      .withTimeout(1.minutes)
      .withIdleConnectionTime(10.minutes)
      .build

  private val headers = 
    Headers(
      Accept(MediaType.application.json)
    )
                  
end RemoteActorRef

