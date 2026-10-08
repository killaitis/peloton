package peloton

import peloton.actor.ActorSystem
import peloton.actor.Actor
import peloton.actors.CollectorActor
import peloton.config.Config

import cats.effect.{Deferred, FiberIO, IO, Outcome}
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.net.{InetAddress, ServerSocket, URI}

class ActorSystemSpec
    extends AsyncFlatSpec
      with AsyncIOSpec
      with Matchers:

  behavior of "An ActorSystem"

  it should "not let a terminated reference unregister a replacement actor" in:
    ActorSystem.use: actorSystem ?=>
      import CollectorActor.Message.*
      import CollectorActor.Response.*

      for
        previous <- CollectorActor.spawn("reused")
        _        <- previous.terminate
        current  <- CollectorActor.spawn("reused")
        _        <- current ? Add("current")
        _        <- previous.terminate
        resolved <- actorSystem.actorRef[CollectorActor.Message]("reused")
        response <- resolved ? Get
        _         = response shouldBe GetResponse(List("current"))
        _        <- current.terminate
      yield ()

  it should "reject new actors after shutdown" in:
    ActorSystem.use: actorSystem ?=>
      for
        _      <- actorSystem.shutdown
        result <- CollectorActor.spawn("after-shutdown").attempt
        _       = result.isLeft shouldBe true
      yield ()

  it should "reject messages and fail an in-flight ask after termination" in:
    ActorSystem.use: actorSystem ?=>
      given Actor.CanAsk[Int, Int] = Actor.canAsk

      for
        actor <- actorSystem.spawnActor[Unit, Int](
                   initialState = (),
                   initialBehavior = (_, _, _) => IO.never,
                   name = Some("stopped-actor")
                 )
        askFiber <- (actor ? 1).start
        _        <- IO.cede
        _        <- actor.terminate
        askResult <- askFiber.joinWithNever.attempt
        tellResult <- (actor ! 2).attempt
        _           = askResult.left.toOption.exists(_.isInstanceOf[Actor.ActorTerminatedException]) shouldBe true
        _           = tellResult.left.toOption.exists(_.isInstanceOf[Actor.ActorTerminatedException]) shouldBe true
      yield ()

  it should "backpressure external messages when inboxCapacity is reached" in:
    ActorSystem.use: actorSystem ?=>
      for
        entered <- Deferred[IO, Unit]
        release <- Deferred[IO, Unit]
        actor   <- actorSystem.spawnActor[Unit, Int](
                     initialState = (),
                     initialBehavior = (_, message, context) =>
                       (if message == 1 then entered.complete(()).void >> release.get else IO.unit) >>
                         context.currentBehaviorM,
                     name = Some("bounded-inbox"),
                     inboxCapacity = Some(1)
                   )
        _       <- actor.tell(1)
        _       <- entered.get
        _       <- actor.tell(2)
        result  <- IO.race(actor.tell(3), IO.sleep(100.millis))
        _        = result.isRight shouldBe true
        _       <- release.complete(()).void
        _       <- actor.tell(4)
      yield ()

  it should "schedule a delayed message to itself" in:
    ActorSystem.use: actorSystem ?=>
      sealed trait TimerMessage
      case object StartTimer extends TimerMessage
      case object TimerFired extends TimerMessage
      case object ReadState extends TimerMessage
      given Actor.CanAsk[ReadState.type, Boolean] = Actor.canAsk

      for
        actor <- actorSystem.spawnActor[Boolean, TimerMessage](
                   initialState = false,
                   initialBehavior = (fired, message, context) => message match
                     case StartTimer =>
                       context.scheduleOnce(25.millis, TimerFired).void >> context.currentBehaviorM
                     case TimerFired => context.setState(true)
                     case ReadState  => context.reply(fired),
                   name = Some("timer-actor")
                 )
        _      <- actor ! StartTimer
        _      <- IO.sleep(75.millis)
        result <- actor ? ReadState
        _       = result shouldBe true
      yield ()

  it should "cancel scheduled timers when the actor terminates" in:
    ActorSystem.use: actorSystem ?=>
      for
        scheduledFiber <- Deferred[IO, FiberIO[Unit]]
        actor <- actorSystem.spawnActor[Unit, Int](
                   initialState = (),
                   initialBehavior = (_, message, context) =>
                     if message == 0 then
                       context.scheduleOnce(5.seconds, 1).flatMap(fiber => scheduledFiber.complete(fiber).as(context.currentBehavior))
                     else context.currentBehaviorM,
                   name = Some("timer-cancel-actor")
                 )
        _       <- actor.tell(0)
        fiber   <- scheduledFiber.get
        _       <- actor.terminate
        outcome <- fiber.join
        _        = outcome match
                     case Outcome.Canceled() => succeed
                     case _                  => fail(s"expected canceled timer fiber, got $outcome")
      yield ()

  it should "extract remote actor names from non-empty URI paths" in:
    ActorSystem.use: actorSystem ?=>
      for
        remoteRef <- actorSystem.remoteActorRef[Any](URI("peloton://localhost:5000/remote%20actor"))
        _          = remoteRef.name shouldBe "remote actor"
        emptyPath <- actorSystem.remoteActorRef[Any](URI("peloton://localhost:5000/")).attempt
        _          = emptyPath.isLeft shouldBe true
      yield ()

  it should "fail acquisition when the configured HTTP port is unavailable" in:
    IO.blocking(new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))).bracket { socket =>
      val config = Config(
        Config.Peloton(http = Some(Config.Http("127.0.0.1", socket.getLocalPort)))
      )

      ActorSystem.make(config)
        .flatMap(_.use(_ => IO.unit))
        .attempt
        .map(result => result.isLeft shouldBe true)
    } { socket =>
      IO.blocking(socket.close())
    }

end ActorSystemSpec