package peloton

import peloton.actor.ActorSystem
import peloton.actors.CollectorActor
import peloton.config.Config

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

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