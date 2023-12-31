package peloton

import peloton.persistence.DurableStateStore
import peloton.persistence.DurableStateStore.*
import peloton.persistence.DurableState
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec

import peloton.DurableStateStoreSpec.*
import peloton.DurableStateStoreSpec.given
import peloton.config.Config

import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers


abstract class DurableStateStoreSpec
  extends AsyncFlatSpec 
    with AsyncIOSpec 
    with Matchers:

  val config: Config

  behavior of "A DurableStateStore"

  it should "not find persisted states when the store is empty (either freshly created or cleared)" in:
    DurableStateStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()
        _      <- store.read(persistenceId) asserting { _ shouldBe None }
        
        state   = DurableState(payload   = MyData(i = 33, s = "Scala"), 
                              revision  = 1L, 
                              timestamp = 12345L
                              )

        _      <- store.write(persistenceId, state)
        _      <- store.clear()
        _      <- store.read(persistenceId) asserting { _ shouldBe None }
      yield ()

  it should "insert a state with revision 1 when the state has not been inserted yet" in:
    DurableStateStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()

        state   = DurableState(payload   = MyData(i = 33, s = "Scala"), 
                              revision  = 1L, 
                              timestamp = 12345L
                              )

        _      <- store.write(persistenceId, state)
        _      <- store.read(persistenceId) asserting { _ shouldBe Some(state) }
      yield ()

  it should "insert a new state with the following revision when the state has already been inserted" in:
    DurableStateStore.use(config): store ?=> 
      for
        _        <- store.drop()
        _        <- store.create()

        oldState  = DurableState(payload = MyData(i = 33, s = "Scala"),
                                revision = 1L,
                                timestamp = 12345L
                                )

        newState  = DurableState(payload = MyData(i = 26, s = "Cats Effect"),
                                revision = oldState.revision + 1, // the following revision
                                timestamp = 23456L
                                )

        _        <- store.write(persistenceId, oldState)
        _        <- store.write(persistenceId, newState)
        _        <- store.read(persistenceId) asserting { _ shouldBe Some(newState) }
      yield ()

  it should "not insert a state with a revision >1 when the state has not been inserted yet" in:
    DurableStateStore.use(config): store ?=> 
      for
        _      <- store.drop()
        _      <- store.create()

        state   = DurableState(payload   = MyData(i = 33, s = "Scala"), 
                              revision  = 33L, // initial revision must be 1!
                              timestamp = 12345L
                              )

        _      <- store.write(persistenceId, state).assertThrows[RevisionMismatchError]
      yield ()

  it should "not insert a state with a revision gap" in:
    DurableStateStore.use(config): store ?=> 
      for
        _        <- store.drop()
        _        <- store.create()

        oldState  = DurableState(payload = MyData(i = 33, s = "Scala"),
                                revision = 1L,
                                timestamp = 12345L
                                )

        newState  = DurableState(payload = MyData(i = 26, s = "Cats Effect"),
                                revision = oldState.revision + 22, // a gap in revisions
                                timestamp = 23456L
                                )

        _        <- store.write(persistenceId, oldState)
        _        <- store.write(persistenceId, newState).assertThrows[RevisionMismatchError]
      yield ()

  it should "not insert a state with the same revision" in:
    DurableStateStore.use(config): store ?=> 
      for
        _        <- store.drop()
        _        <- store.create()

        oldState  = DurableState(payload = MyData(i = 33, s = "Scala"),
                                revision = 1L,
                                timestamp = 12345L
                                )

        newState  = DurableState(payload = MyData(i = 26, s = "Cats Effect"),
                                revision = oldState.revision,
                                timestamp = 23456L
                                )

        _        <- store.write(persistenceId, oldState)
        _        <- store.write(persistenceId, newState).assertThrows[RevisionMismatchError]
      yield ()

end DurableStateStoreSpec


object DurableStateStoreSpec:
  final case class MyData(i: Int, s: String)

  given PayloadCodec[MyData] = persistence.KryoPayloadCodec.create

  val persistenceId = PersistenceId.of("myitem")
