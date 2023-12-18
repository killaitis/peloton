package peloton

import peloton.actor.ActorSystem
import peloton.actor.ActorRef
import peloton.config.Config
import peloton.config.Config.*

import peloton.actors.HelloActor
import peloton.actors.FooActor

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

import java.net.URI
import org.http4s.client.UnexpectedStatus

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
        _      <- HelloActor.spawn("HelloActor")
        actor  <- ActorRef.of[HelloActor.Message](URI("peloton://localhost:5000/HelloActor"))
        _      <- actor ! HelloActor.Message.Hello("Hello, dear actor!")
        _      <- (actor ? HelloActor.Message.HowAreYou).asserting:
                    _ shouldBe HelloActor.Message.HowAreYouResponse("I'm fine")
      yield ()

  it should "handle messages to non-resolvable actors" in:    
    ActorSystem.use(config): _ ?=> 
      for
        _      <- HelloActor.spawn("HelloActor")
        actor  <- ActorRef.of[HelloActor.Message](URI("peloton://localhost:5000/SomeOtherActor")) // <-- does not exist
        _      <- (actor ! HelloActor.Message.Hello("Hello, dear actor!")).assertThrows[UnexpectedStatus]
      yield ()

  it should "handle invalid message types" in:    
    ActorSystem.use(config): _ ?=> 
      for
        _      <- HelloActor.spawn("HelloActor")
        actor  <- ActorRef.of[FooActor.Message](URI("peloton://localhost:5000/HelloActor")) // <-- invalid type
        _      <- (actor ! FooActor.Set(3, 2)).assertThrows[UnexpectedStatus]
      yield ()
