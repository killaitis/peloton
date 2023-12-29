package peloton

import peloton.persistence.EventStore
import peloton.persistence.Event
import peloton.persistence.Snapshot
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.Retention
import peloton.config.Config

import peloton.EventStoreSpec.*
import peloton.EventStoreSpec.given

import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers


abstract class EventStoreSpec
  extends AsyncFlatSpec 
    with AsyncIOSpec 
    with Matchers:

  val config: Config

  behavior of "An EventStore"

  it should "not find events when the store is empty (either freshly created or cleared)" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.readEvents[MyState, MyEvent](persistenceId, false)
                    .compile.toList.asserting { 
                      _ shouldBe List.empty 
                    }
        event   = Event(payload   = MyEvent(i = 33, s = "Scala"), timestamp = 12345L)
        _      <- store.writeEvent(persistenceId, event)
        _      <- store.clear()
        _      <- store.readEvents[MyState, MyEvent](persistenceId, false)
                    .compile.toList.asserting { 
                      _ shouldBe List.empty 
                    }
      yield ()

  it should "be able to insert events into the event store and retrieve them in correct order" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        event1  = Event(payload = MyEvent(i = 24, s = "Peloton"), timestamp = 93846L)
        event2  = Event(payload = MyEvent(i = 33, s = "Scala"),   timestamp = 12345L)
        event3  = Event(payload = MyEvent(i = 16, s = "Cats"),    timestamp = 44284L)
        _      <- store.writeEvent(persistenceId, event1)
        _      <- store.writeEvent(persistenceId, event2)
        _      <- store.writeEvent(persistenceId, event3)
        _      <- store.readEvents[MyState, MyEvent](persistenceId, false)
                    .compile.toList.asserting { 
                      _ shouldBe List(event1, event2, event3) 
                    }
      yield ()

  it should "handle the order of events with the same timestamp correctly" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        event1  = Event(payload = MyEvent(i = 24, s = "Peloton"), timestamp = 100L)
        event2  = Event(payload = MyEvent(i = 33, s = "Scala"),   timestamp = 100L)
        event3  = Event(payload = MyEvent(i = 16, s = "Cats"),    timestamp = 100L)
        _      <- store.writeEvent(persistenceId, event1)
        _      <- store.writeEvent(persistenceId, event2)
        _      <- store.writeEvent(persistenceId, event3)
        _      <- store.readEvents[MyState, MyEvent](persistenceId, false)
                    .compile.toList.asserting { 
                    _ shouldBe List(event1, event2, event3)
                  }
      yield ()

  it should "skip all events inserted before the last snapshot when reading the event store" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.writeEvent    (persistenceId, event1)
        _      <- store.writeEvent    (persistenceId, event2)
        _      <- store.writeSnapshot (persistenceId, snap1, noPurging)
        _      <- store.writeEvent    (persistenceId, event3)
        _      <- store.writeSnapshot (persistenceId, snap2, noPurging)
        _      <- store.writeEvent    (persistenceId, event4)
        _      <- store.writeEvent    (persistenceId, event5)

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = true)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap2, event4, event5)
                    }
      yield ()

  it should "keep all events and snapshots if purging is disabled" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.writeEvent    (persistenceId, event1)
        _      <- store.writeEvent    (persistenceId, event2)
        _      <- store.writeSnapshot (persistenceId, snap1, noPurging)
        _      <- store.writeEvent    (persistenceId, event3)
        _      <- store.writeSnapshot (persistenceId, snap2, noPurging)
        _      <- store.writeEvent    (persistenceId, event4)
        _      <- store.writeEvent    (persistenceId, event5)

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = false)
                    .compile.toList.asserting { 
                      _ shouldBe List(event1, event2, snap1, event3, snap2, event4, event5)
                    }

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = true)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap2, event4, event5)
                    }
      yield ()

  it should "purge outdated events and snapshots if purging is enabled (keep 1 snapshot)" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.writeEvent    (persistenceId, event1)
        _      <- store.writeEvent    (persistenceId, event2)
        _      <- store.writeSnapshot (persistenceId, snap1, Retention(purgeOnSnapshot = true, snapshotsToKeep = 1))
        _      <- store.writeEvent    (persistenceId, event3)
        _      <- store.writeSnapshot (persistenceId, snap2, Retention(purgeOnSnapshot = true, snapshotsToKeep = 1))
        _      <- store.writeEvent    (persistenceId, event4)
        _      <- store.writeEvent    (persistenceId, event5)

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = false)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap2, event4, event5)
                    }

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = true)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap2, event4, event5)
                    }
      yield ()

  it should "purge outdated events and snapshots if purging is enabled (keep 2 snapshots)" in:
    EventStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.writeEvent    (persistenceId, event1)
        _      <- store.writeEvent    (persistenceId, event2)
        _      <- store.writeSnapshot (persistenceId, snap1, Retention(purgeOnSnapshot = true, snapshotsToKeep = 2))
        _      <- store.writeEvent    (persistenceId, event3)
        _      <- store.writeSnapshot (persistenceId, snap2, Retention(purgeOnSnapshot = true, snapshotsToKeep = 2))
        _      <- store.writeEvent    (persistenceId, event4)
        _      <- store.writeEvent    (persistenceId, event5)

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = false)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap1, event3, snap2, event4, event5)
                    }

        _      <- store.readEvents[MyState, MyEvent](persistenceId, startFromLatestSnapshot = true)
                    .compile.toList.asserting { 
                      _ shouldBe List(snap2, event4, event5)
                    }
      yield ()

end EventStoreSpec


object EventStoreSpec:
  final case class MyEvent(i: Int, s: String)
  final case class MyState(x: Int)

  given PayloadCodec[MyEvent] = persistence.KryoPayloadCodec.create
  given PayloadCodec[MyState] = persistence.KryoPayloadCodec.create

  val persistenceId = PersistenceId.of("my-event")

  val noPurging = Retention(purgeOnSnapshot = false, snapshotsToKeep = 0)

  val event1  = Event(payload = MyEvent(i = 24, s = "Peloton"), timestamp = 10000L)
  val event2  = Event(payload = MyEvent(i = 33, s = "Scala"),   timestamp = 10001L)
  val snap1   = Snapshot(payload = MyState(x = 23),             timestamp = 10001L)
  val event3  = Event(payload = MyEvent(i = 16, s = "Cats"),    timestamp = 10001L)
  val snap2   = Snapshot(payload = MyState(x = 10),             timestamp = 10001L)
  val event4  = Event(payload = MyEvent(i = 11, s = "Sarah"),   timestamp = 10002L)
  val event5  = Event(payload = MyEvent(i = 22, s = "Mary"),    timestamp = 10003L)

