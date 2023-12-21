package peloton.persistence

import cats.effect.IO
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
    * Reads all [[Event]]s for type `A` from the storage backend.
    *
    * @param persistenceId
    *   The [[PersistenceId]] of the instance to read
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert instances of type `A` to a byte array and vice versa
    * @return
    *   An `fs2.Stream` of [[Event]]
    */
  def readEvents[A](persistenceId: PersistenceId)(using payloadCodec: PayloadCodec[A]): Stream[IO, Event[A]] = 
    readEncodedEvents(persistenceId)
      .evalMap { encodedEvent => 
        payloadCodec
          .decode(encodedEvent.payload)
          .map(a => Event(payload = a, timestamp = encodedEvent.timestamp)) 
      }
    
  /**
    * Writes a new [[Event]] of type `A` into storage backend.
    *
    * @param persistenceId
    *   The [[PersistenceId]] of the durable state instance to write
    * @param state
    *   The [[DurableState]] of type `A`
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert instances of type `A` to a byte array and vice versa
    * @return
    *   An `IO[Unit]`
    */
  def writeEvent[A](persistenceId: PersistenceId, event: Event[A])(using payloadCodec: PayloadCodec[A]): IO[Unit] = 
    for
      encodedPayload   <- payloadCodec.encode(event.payload)
      encodedEvent      = EncodedEvent(payload   = encodedPayload, 
                                       timestamp = event.timestamp
                                      )
      _                <- writeEncodedEvent(persistenceId, encodedEvent)
    yield ()

end EventStore