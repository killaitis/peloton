package peloton

import peloton.actor.ActorSystem
import peloton.persistence.*

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues
import utils.EventStoreMock

import actors.CalculatorActor
import peloton.actor.SnapshotPredicate


class EventSourcedActorSpec
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with OptionValues
      with Matchers:

  behavior of "An EventSourcedActor"

  import EventSourcedActorSpec.*
  import EventSourcedActorSpec.given

  val eventStore = summon[EventStore]

  it should "spawn a new event sourced actor and store its events" in:
    ActorSystem.use: _ ?=> 
      for
        _      <- eventStore.clear()

        actor  <- CalculatorActor.spawn(persistenceId)
        _      <- actor ! CalculatorActor.Add(23)
        _      <- actor ! CalculatorActor.Add(11)
        _      <- actor ! CalculatorActor.Sub(5)
        _      <- actor ! CalculatorActor.Add(2)

        _      <- (actor ? CalculatorActor.GetState).asserting:
                    _ shouldBe CalculatorActor.GetStateResponse(value = 31)
                    
        _      <- readEvents.asserting:
                    _ shouldBe List(
                        CalculatorActor.AddEvent(23),
                        CalculatorActor.AddEvent(11),
                        CalculatorActor.SubEvent(5),
                        CalculatorActor.AddEvent(2)
                      )
        _      <- actor.terminate
      yield ()

  it should "spawn a new event sourced actor and read all previous events from the event store" in:
    ActorSystem.use: _ ?=> 
      for
        _      <- eventStore.clear()

        actor  <- CalculatorActor.spawn(persistenceId)
        _      <- actor ! CalculatorActor.Add(23)
        _      <- actor ! CalculatorActor.Add(11)
        _      <- actor ! CalculatorActor.Sub(5)
        _      <- actor ! CalculatorActor.Add(2)
        state1 <- actor ? CalculatorActor.GetState
        _      <- actor.terminate


        actor  <- CalculatorActor.spawn(persistenceId)
        state2 <- actor ? CalculatorActor.GetState
        _      <- IO.pure:
                    state1 shouldBe state2
        _      <- actor.terminate
      yield ()

  it should "create snapshots when the snapshot predicate is matching " in:
    ActorSystem.use: _ ?=> 
      for
        _      <- eventStore.clear()

        actor  <- CalculatorActor.spawn(persistenceId     = persistenceId, 
                                        snapshotPredicate = SnapshotPredicate.snapshotEvery(3)
                                       )
        _      <- actor ! CalculatorActor.Add(23)
        _      <- actor ! CalculatorActor.Add(11)
        _      <- actor ! CalculatorActor.Sub(5) // <- snapshot: 23+11-5 = 29
        _      <- actor ! CalculatorActor.Add(2)
        _      <- actor ! CalculatorActor.Add(6)
        state1 <- actor ? CalculatorActor.GetState
        _      <- actor.terminate

        _      <- readEvents.asserting:
                    _ shouldBe List(
                        CalculatorActor.State(29),
                        CalculatorActor.AddEvent(2),
                        CalculatorActor.AddEvent(6)
                      )
      yield ()

end EventSourcedActorSpec


object EventSourcedActorSpec:
  private given eventStore: EventStore = new EventStoreMock
  
  private val persistenceId = PersistenceId.of("my-event-sourced-actor")

  private def readEvents(using eventStore: EventStore) = 
    eventStore
      .readEvents[CalculatorActor.State, CalculatorActor.Event](persistenceId, startFromLatestSnapshot = true)
      .compile
      .toList
      .map(_.map {
          case snapshot: Snapshot[CalculatorActor.State] => snapshot.payload
          case event: Event[CalculatorActor.Event]       => event.payload
      })
