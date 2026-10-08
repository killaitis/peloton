package peloton

import peloton.actor.ActorSystem
import peloton.actor.Actor
import peloton.actor.ActorRef
import peloton.config.Config
import peloton.config.Config.*

import peloton.actors.GreetingActor
import peloton.actors.FooActor

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import scala.concurrent.duration.*

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.URI
import org.http4s.client.UnexpectedStatus
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString

class RemoteActorSpec
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with Matchers:

  behavior of "A RemoteActor"

  // A Peloton config with HTTP transport enabled
  val config = Config(Peloton(Some(Http("localhost", 5000))))

  it should "be able to receive messages via HTTP requests" in:    
    ActorSystem.use(config): _ ?=> 
      for
        _      <- GreetingActor.spawn("GreetingActor")
        actor  <- ActorRef.of[GreetingActor.Message](URI("peloton://localhost:5000/GreetingActor"))
        _      <- actor ! GreetingActor.Message.Greet("Hello, dear actor!")
        _      <- (actor ? GreetingActor.Message.HowAreYou).asserting:
                    _ shouldBe GreetingActor.Response.HowAreYouResponse("I'm fine")
      yield ()

  it should "handle messages to non-resolvable actors" in:    
    ActorSystem.use(config): _ ?=> 
      for
        _      <- GreetingActor.spawn("GreetingActor")
        actor  <- ActorRef.of[GreetingActor.Message](URI("peloton://localhost:5000/SomeOtherActor")) // <-- does not exist
        _      <- (actor ! GreetingActor.Message.Greet("Hello, dear actor!")).assertThrows[UnexpectedStatus]
      yield ()

  it should "handle invalid message types" in:    
    ActorSystem.use(config): _ ?=> 
      for
        _      <- GreetingActor.spawn("GreetingActor")
        actor  <- ActorRef.of[FooActor.Message](URI("peloton://localhost:5000/GreetingActor")) // <-- invalid message type
        _      <- (actor ! FooActor.Message.Set(3, 2)).assertThrows[UnexpectedStatus]
      yield ()

  it should "accept primitive messages through the remote protocol" in:
    ActorSystem.use(config): actorSystem ?=>
      given Actor.CanAsk[Int, Int] = Actor.canAsk

      for
        _ <- actorSystem.spawnActor[Int, Int](
               initialState = 0,
               initialBehavior = (_, message, context) => context.reply(message),
               name = Some("PrimitiveActor")
             )
        actor    <- ActorRef.of[Int](URI("peloton://localhost:5000/PrimitiveActor"))
        response <- actor ? 42
        _         = response shouldBe 42
      yield ()

  it should "enforce the remote ASK timeout on the server" in:
    ActorSystem.use(config): actorSystem ?=>
      given Actor.CanAsk[Int, Int] = Actor.canAsk

      for
        _ <- actorSystem.spawnActor[Unit, Int](
               initialState = (),
               initialBehavior = (_, message, context) => IO.sleep(250.millis) >> context.reply(message),
               name = Some("SlowActor")
             )
        actor  <- ActorRef.of[Int](URI("peloton://localhost:5000/SlowActor"))
        result <- actor.ask(42, timeout = 20.millis).attempt
        _       = result.left.toOption.exists(_.isInstanceOf[UnexpectedStatus]) shouldBe true
      yield ()

  it should "allow browser preflight requests for both remote endpoints" in:
    ActorSystem.use(config): _ ?=>
      EmberClientBuilder.default[IO].build.use: client =>
        def preflight(path: String) =
          val request = Request[IO](
            method = Method.OPTIONS,
            uri = Uri.unsafeFromString(s"http://localhost:5000/$path"),
            headers = Headers(
              Header.Raw(CIString("Origin"), "https://client.example"),
              Header.Raw(CIString("Access-Control-Request-Method"), "POST"),
              Header.Raw(CIString("Access-Control-Request-Headers"), "content-type")
            )
          )

          client.run(request).use: response =>
            IO:
              val responseHeaders = response.headers.headers.map(header => header.name.toString.toLowerCase -> header.value).toMap
              (
                response.status,
                responseHeaders.get("access-control-allow-origin"),
                responseHeaders.get("access-control-allow-methods"),
                responseHeaders.get("access-control-allow-headers")
              ) shouldBe (
                Status.Ok,
                Some("*"),
                Some("POST, OPTIONS"),
                Some("Content-Type, Accept")
              )

        preflight("tell") >> preflight("ask")
