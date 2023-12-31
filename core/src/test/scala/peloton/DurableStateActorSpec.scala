package peloton

import peloton.actor.ActorSystem
import peloton.utils.*
import peloton.persistence.*

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues
import utils.DurableStateStoreMock

import actors.CountingActor
import actors.CountingActor.Command.*
import actors.CountingActor.Response.*


class DurableStateActorSpec
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with OptionValues
      with Matchers:

  behavior of "A DurableStateActor"

  import DurableStateActorSpec.*
  import DurableStateActorSpec.given

  val store = summon[DurableStateStore]

  it should "spawn a new actor with default values when the actor has never been started" in:
    ActorSystem.use: _ ?=> 
      for
        _      <- store.clear()
        actor  <- CountingActor.spawn(persistenceId)
        _      <- (actor ? GetState).asserting:
                    _ shouldBe GetStateResponse(isOpen = false, counter = 0)
        _      <- actor.terminate
      yield ()

  it should "modify its state when receiving messages and write it to the durable state store" in:
    ActorSystem.use: _ ?=>
      for
        _      <- store.clear()

        incs    = 42
        
        actor  <- CountingActor.spawn(persistenceId)
        _      <- store.read(persistenceId).asserting:
                    // Just spawning an actor should not create a version in the store. 
                    // Only sending commands to the actor should.
                    _ shouldBe None

        _      <- actor ! Open
        _      <- (1 to incs).traverse_(_ => actor ! Inc)

        _      <- (actor ? GetState).asserting:
                    _ shouldBe GetStateResponse(isOpen = true, counter = incs) 
                    
        _      <- (store.read(persistenceId)).asserting:
                    case Some(persistence.DurableState(CountingActor.State(`incs`), `incs`, _)) => succeed
                    case _ => fail()

        _      <- actor.terminate
      yield ()

  it should "re-use its persistent state" in:
    
    // Creates a new actor, increments its counter by a given number and terminates the actor
    ActorSystem.use: _ ?=>
      def runActor(numberOfIncrements: Int) = 
        for
          actor  <- CountingActor.spawn(persistenceId)
          _      <- actor ! Open
          _      <- (1 to numberOfIncrements).traverse_(_ => actor ! Inc)
          _      <- actor ? Close // use ASK to ensure that all previous messages have been processed
          _      <- actor.terminate
        yield ()

      for
        _      <- store.clear()

        _      <- runActor(3)
        _      <- runActor(2)
        _      <- runActor(7)

        actor  <- CountingActor.spawn(persistenceId)
        _      <- (actor ? GetState).asserting:
                    // The actor must have started with a fresh non-persistent state, i.e., isOpen=false, 
                    // and a persistent state with 12 increments from 3 runs
                    _ shouldBe GetStateResponse(isOpen = false, counter = 12)

        _      <- store.read(persistenceId).asserting:
                    case Some(persistence.DurableState(CountingActor.State(12), 12, _)) => succeed
                    case _ => fail()

        _      <- actor.terminate
      yield ()

  it should "handle the actor's message stash appropriately" in:
    ActorSystem.use: _ ?=>
      for
        _      <- store.clear()

        actor  <- CountingActor.spawn(persistenceId)

        // Send two Inc messages when the actor's gate is not open. This should stash the messages.
        _      <- actor ! Inc
        _      <- actor ! Inc
        _      <- (actor ? GetState).asserting:
                    _ shouldBe GetStateResponse(isOpen = false, counter = 0)

        // Now open the actor's gate. This should unstash all messages and hande them
        _      <- actor ! Open
        _      <- (actor ? GetState).asserting:
                    _ shouldBe GetStateResponse(isOpen = true, counter = 2)

        // Now that the actor's gate is open, an Inc message should increment the counter
        _      <- actor ! Inc
        _      <- (actor ? GetState).asserting:
                    _ shouldBe GetStateResponse(isOpen = true, counter = 3)

        _      <- actor.terminate
      yield ()

  it should "handle failed effects in the message handler" in:
    ActorSystem.use: _ ?=>
      for
        _      <- store.clear()
        actor  <- CountingActor.spawn(persistenceId)
        _      <- actor
                    .ask(Fail)
                    .attempt
                    .asserting { _ shouldBe Left(CountingActor.CountingException) }
        _      <- actor.terminate
      yield ()

end DurableStateActorSpec

object DurableStateActorSpec:
  private given PayloadCodec[CountingActor.State] = persistence.KryoPayloadCodec.create
  private given DurableStateStore = new DurableStateStoreMock
  
  private val persistenceId = PersistenceId.of("my-persistent-actor")

end DurableStateActorSpec