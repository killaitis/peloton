package peloton

import peloton.actor.ActorSystem

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.*

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

import actors.CascadingActor
import actors.CascadingActor.*
import actors.CollectorActor
import actors.CollectorActor.*
import actors.EffectActor

import StatefulActorSpec.*

class StatefulActorSpec
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with Matchers:

  behavior of "A StatefulActor"

  it should "spawn a new actor" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- CollectorActor.spawn()
        _      <- actor ? Get() asserting { _ shouldBe GetResponse(words = Nil) }
        _      <- actor.terminate
      yield ()

  it should "handle messages sent by the ASK pattern" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- CollectorActor.spawn()
        _      <- actor ? Add("Actors") asserting { _ shouldBe AddResponse(wordAdded = "Actors") }
        _      <- actor ? Add("are")    asserting { _ shouldBe AddResponse(wordAdded = "are") }
        _      <- actor ? Add("great")  asserting { _ shouldBe AddResponse(wordAdded = "great") }
        _      <- actor ? Get()         asserting { _ shouldBe GetResponse(words = "Actors" :: "are" :: "great" :: Nil) }
        _      <- actor.terminate
      yield ()

  it should "handle messages sent by the TELL pattern" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- CollectorActor.spawn()
        words   = "Actor" :: "tests" :: "are" :: "very" :: "important" :: Nil
        _      <- words.traverse(word => actor ! Add(word))
        _      <- actor ? Get() asserting { _ shouldBe GetResponse(words) }
        _      <- actor.terminate
      yield ()

  it should "be able to cascade ASK messages" in:
    ActorSystem.use: _ ?=> 
      for
        actorC <- CascadingActor.spawn("Actor C", None)
        actorB <- CascadingActor.spawn("Actor B", Some(actorC))
        actorA <- CascadingActor.spawn("Actor A", Some(actorB))

        _      <- actorA ? Hello("Peter") asserting { 
                    _ shouldBe HelloResponse("Actor A" :: "Actor B" :: "Actor C" :: "Peter" :: Nil)
                  }
                    
        _      <- actorB ? Hello("Sarah") asserting {
                    _ shouldBe HelloResponse("Actor B" :: "Actor C" :: "Sarah" :: Nil)
                  }

        _      <- actorC ? Hello("Stephanie") asserting {
                    _ shouldBe HelloResponse("Actor C" :: "Stephanie" :: Nil)
                  }

        _      <- actorA.terminate
        _      <- actorB.terminate
        _      <- actorC.terminate
      yield ()
  
  it should "be able to pipe messages from a running fiber to itself" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- EffectActor.spawn(effect = meaningOfLifeEffect)

        // Initially, there should no result.
        _      <- actor ? EffectActor.Get() asserting { _ shouldBe EffectActor.GetResponse(meaningOfLife = None) }

        // Start the effect
        _      <- actor ? EffectActor.Run() asserting { _ shouldBe EffectActor.RunResponse(effectStarted = true) }
        
        // wait for 5 second. The effect should then be finished
        _      <- IO.sleep(5.seconds)

        // Check the result of the completed effect
        _      <- actor ? EffectActor.Get() asserting { _ shouldBe EffectActor.GetResponse(meaningOfLife = Some(42)) }

        _      <- actor.terminate
      yield ()

  it should "be able to cancel the effect fiber" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- EffectActor.spawn(effect = meaningOfLifeEffect)

        // Start the effect and wait for 1 second. The effect should be running by then
        _      <- actor ? EffectActor.Run() asserting { _ shouldBe EffectActor.RunResponse(effectStarted = true) }
        _      <- IO.sleep(1.second)

        // Cancel the effect. This should succeed.
        _      <- actor ? EffectActor.Cancel() asserting { _ shouldBe EffectActor.CancelResponse(effectCancelled = true) }

        // Cancel the effect again. This should fail, as no effect should be running by now.
        _      <- actor ? EffectActor.Cancel() asserting { _ shouldBe EffectActor.CancelResponse(effectCancelled = false) }

        _      <- actor.terminate
      yield ()

  it should "be able to handle a failing effect" in:
    ActorSystem.use: _ ?=> 
      for
        actor  <- EffectActor.spawn(effect = IO.raiseError(RuntimeException()) *> meaningOfLifeEffect)

        // Start the effect and wait for 2 seconds. The effect should have failed by then
        _      <- actor ? EffectActor.Run() asserting { _ shouldBe EffectActor.RunResponse(effectStarted = true) }
        _      <- IO.sleep(2.second)
        _      <- actor ? EffectActor.Get() asserting { _ shouldBe EffectActor.GetResponse(meaningOfLife = None) }

        _      <- actor.terminate
      yield ()

end StatefulActorSpec

object StatefulActorSpec:
  val meaningOfLifeEffect = 
    ( for
        _    <- IO.println("Calculating Meaning Of Life")
        _    <- IO.sleep(2.seconds)
        mol   = 42
        _    <- IO.println("Meaning Of Live has been calculated!")
      yield mol
    )
      .onCancel(IO.println("Calculation of Meaning Of Live has been cancelled!"))
      .onError (err => IO.println(s"Calculation of Meaning Of Live has failed: ${err.getMessage()}"))

end StatefulActorSpec
