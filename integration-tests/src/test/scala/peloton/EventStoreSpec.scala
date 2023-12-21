package peloton

import peloton.persistence.EventStore
// import peloton.persistence.EventStore.*
import peloton.persistence.Event
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec

import peloton.EventStoreSpec.*
import peloton.EventStoreSpec.given

import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.*
import io.circe.generic.semiauto.*
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers


abstract class EventStoreSpec
  extends AsyncFlatSpec 
    with AsyncIOSpec 
    with Matchers:

  val store: EventStore

  behavior of "An EventStore"

  it should "not find events when the store is empty (either freshly created or cleared)" in:
    for
      _      <- store.drop()
      _      <- store.create()
      _      <- store.readEvents(persistenceId).compile.toList asserting { _ shouldBe List.empty }
      event   = Event(payload   = MyEvent(i = 33, s = "Scala"), timestamp = 12345L)
      _      <- store.writeEvent(persistenceId, event)
      _      <- store.clear()
      _      <- store.readEvents(persistenceId).compile.toList asserting { _ shouldBe List.empty }
    yield ()

  it should "be able to insert events into the event store and retrieve them in correct order." in:
    for
      _      <- store.drop()
      _      <- store.create()
      event1   = Event(payload   = MyEvent(i = 24, s = "Peloton"), timestamp = 93846L)
      event2   = Event(payload   = MyEvent(i = 33, s = "Scala"),   timestamp = 12345L)
      event3   = Event(payload   = MyEvent(i = 16, s = "Cats"),    timestamp = 44284L)
      _      <- store.writeEvent(persistenceId, event1)
      _      <- store.writeEvent(persistenceId, event2)
      _      <- store.writeEvent(persistenceId, event3)
      _      <- store.readEvents(persistenceId).compile.toList asserting { 
                  _ shouldBe List(event2, event3, event1) // ordered by event timestamp
                }
    yield ()


end EventStoreSpec


object EventStoreSpec:
  final case class MyEvent(i: Int, s: String)

  given PayloadCodec[MyEvent] = persistence.JsonPayloadCodec.create

  val persistenceId = PersistenceId.of("myitem")
