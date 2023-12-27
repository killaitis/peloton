package peloton.persistence

import peloton.config.Config

import cats.effect.IO
import cats.effect.Resource
import fs2.Stream

trait EventStore:

  /**
    * Create and initialize the internal data structures used by the storage backend if not already created. 
    * 
    * Note: It is guaranteed that this operation will *not* truncate/clear the underlying storage, 
    * but just create it in case it does not already exist. If you need to clear the storage, use 
    * method [[clear]] instead.
    *
    * @return an `IO[Unit]`
    */
  def create(): IO[Unit]

  /**
    * Drops the internal data structures used by the storage backend. 
    * 
    * @return an `IO[Unit]`
    */
  def drop(): IO[Unit]

  /**
    * Clears/resets the internal data structures used by the storage backend if not already created. 
    * 
    * @return an `IO[Unit]`
    */
  def clear(): IO[Unit]

  /**
    * Reads all encoded (serialized) events for a given `persistenceId` from the storage backend.
    *
    * @param persistenceId 
    *   The [[PersistenceId]] to read
    * @return
    *   An `fs2.Stream` of [[EncodedEvent]]
    */
  def readEncodedEvents(persistenceId: PersistenceId): Stream[IO, EncodedEvent]

  /**
    * Writes an encoded (serialized) event for a given `persistenceId` into the storage backend.
    * 
    * @param persistenceId 
    *   The [[PersistenceId]] under which the encoded event will be written
    * @param encodedEvent
    *   The encoded event of type [[EncodedEvent]]
    * @return
    *   `IO[Unit]`
    */
  def writeEncodedEvent(persistenceId: PersistenceId, encodedEvent: EncodedEvent): IO[Unit]
  
  /**
    * Reads the latest snapshot of payload type `S` (if it exists) and all [[Event]]s of payload 
    * type `E` (created after the latest snapshot or from the beginning if not) for a given 
    * [[PersistenceId]] from the storage backend.
    *
    * @tparam E
    *   The event's payload type
    * @tparam S
    *   The actor's snapshot payload type
    * @param persistenceId
    *   The [[PersistenceId]] of the instance to read
    * @param eventCodec 
    *   a given [[PayloadCodec]] to convert events of payload type `E` to a byte array and vice versa
    * @param snapshotCodec 
    *   a given [[PayloadCodec]] to convert snapshots of payload type `S` to a byte array and vice versa
    * @return
    *   An `fs2.Stream` of either [[Snapshot]] or [[Event]]
    */
  def readEvents[S, E](persistenceId: PersistenceId
                      )(using 
                       eventCodec: PayloadCodec[E],
                       snapshotCodec: PayloadCodec[S]
                      ): Stream[IO, Snapshot[S] | Event[E]] = 
    readEncodedEvents(persistenceId)
      .evalMap { encodedEvent => 
        if encodedEvent.isSnapshot
        then 
          snapshotCodec
            .decode(encodedEvent.payload)
            .map(payload => Snapshot(payload = payload, timestamp = encodedEvent.timestamp))
        else 
          eventCodec
            .decode(encodedEvent.payload)
            .map(payload => Event(payload = payload, timestamp = encodedEvent.timestamp))
      }
    
  /**
    * Writes a new [[Event]] of payload type `E` into the storage backend.
    *
    * @tparam E 
    *   The event's payload type
    * @param persistenceId
    *   The [[PersistenceId]] of the actor to write
    * @param event
    *   The [[Event]] of type `E`
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert events of payload type `A` to a byte array and vice versa
    * @return
    *   An `IO[Unit]`
    */
  def writeEvent[E](persistenceId: PersistenceId, event: Event[E])(using payloadCodec: PayloadCodec[E]): IO[Unit] = 
    for
      encodedPayload   <- payloadCodec.encode(event.payload)
      encodedEvent      = EncodedEvent(payload    = encodedPayload,
                                       timestamp  = event.timestamp,
                                       isSnapshot = false
                                      )
      _                <- writeEncodedEvent(persistenceId, encodedEvent)
    yield ()

  /**
    * Writes a [[Snapshot]] of payload type `S` into the storage backend.
    * 
    * @tparam S
    *   The snapshot's payload type
    * @param persistenceId 
    *   The [[PersistenceId]] of the actor to write
    * @param snapshot
    *   The [[Snapshot]] of type `S`
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert the snapshot of payload type `S` to a byte array and vice versa
    * @return
    *   `IO[Unit]`
    */
  def writeSnapshot[S](persistenceId: PersistenceId, snapshot: Snapshot[S])(using payloadCodec: PayloadCodec[S]): IO[Unit] = 
    for
      encodedPayload   <- payloadCodec.encode(snapshot.payload)
      encodedSnapshot   = EncodedEvent(payload    = encodedPayload,
                                       timestamp  = snapshot.timestamp,
                                       isSnapshot = true
                                      )
      _                <- writeEncodedEvent(persistenceId, encodedSnapshot)
    yield ()

end EventStore

object EventStore:

  def make(): IO[Resource[IO, EventStore]] = 
    for
      config <- Config.default()
      store  <- make(config)
    yield store

  def make(config: Config): IO[Resource[IO, EventStore]] =
    for
      persistenceConfig  <- IO.fromOption(config.peloton.persistence)(new IllegalArgumentException("Invalid peloton config: no persistence section found.")) 
      driver             <- Driver(persistenceConfig.driver)
      eventSore          <- driver.createEventStore(persistenceConfig)
    yield eventSore

  def use[A](config: Config)(f: EventStore ?=> IO[A]): IO[A] = 
    for
      store  <- make(config)
      retval <- store.use { case given EventStore => f }
    yield retval

  def use[A](f: EventStore ?=> IO[A]): IO[A] = 
    for
      store  <- make()
      retval <- store.use { case given EventStore => f }
    yield retval

end EventStore