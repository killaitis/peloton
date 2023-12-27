package peloton.persistence

import peloton.config.Config

import cats.effect.{IO, Resource}


/**
  * The persistence layer typeclass for a given state class `A` that provides methods to store and retrieve
  * instances of type `A` from and to a specific storage backend.
  *
  */
abstract class DurableStateStore:

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
    * Reads the current encoded (serialized) state for a given `persistenceId` from the storage backend.
    *
    * @param persistenceId 
    *   The [[PersistenceId]] of the encoded state instance to read
    * @return
    *   `Some` [[EncodedState]] if the entry exists in the storage backend, else `None`
    */
  def readEncodedState(persistenceId: PersistenceId): IO[Option[EncodedState]]

  /**
    * Writes (creates or replaces) an encoded (serialized) state for a given `persistenceId` into the storage backend.
    *
    * The method will fail if the revision of new encoded state is not exactly the successor of the revision of the 
    * current encoded state, i.e., `newRevision == currentRevision + 1`. This ensures that there is no collision with 
    * persistence IDs that have accidentally been used multiple times.
    * 
    * Implementation note: The revision check could have easily been put into the generic [[write]] method. 
    * This would have eliminated the need to do the logic in each implementation of [[writeEncodedState]], 
    * but it would also have eliminated the possibility to do the logic more efficient. This is why the decision
    * was made to do it here for each implementation.
    * 
    * @param persistenceId 
    *   The [[PersistenceId]] of the encoded state instance to write
    * @param encodedState
    *   The state instance of type [[EncodedState]]
    * @return
    *   `IO[Unit]`
    */
  def writeEncodedState(persistenceId: PersistenceId, encodedState: EncodedState): IO[Unit]

  /**
    * Reads the current revision of the [[DurableState]] for payload type `S` from storage backend.
    *
    * @tparam S
    *   The state's payload type
    * @param persistenceId
    *   The [[PersistenceId]] of the durable state instance to read
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert [[DurableState]] instances of payload type `S` to a byte array and vice versa
    * @return
    *   `Some` [[DurableState]] if the entry exists in the storage backend, else `None`
    */
  def read[S](persistenceId: PersistenceId)(using payloadCodec: PayloadCodec[S]): IO[Option[DurableState[S]]] = 
    for
      maybeEncodedState  <- readEncodedState(persistenceId)
      maybeDecodedState  <- maybeEncodedState match
                              case None => 
                                IO.pure[Option[DurableState[S]]](None)
                              case Some(encodedState) => 
                                payloadCodec
                                  .decode(encodedState.payload)
                                  .map(payload => Some(DurableState(payload = payload, 
                                                                    revision = encodedState.revision, 
                                                                    timestamp = encodedState.timestamp
                                                                   )
                                                      )
                                  )
    yield maybeDecodedState
    
  /**
    * Writes a new revision of the [[DurableState]] for payload type `S` into the storage backend.
    *
    * The method will fail if the revision of new encoded state is not exactly the successor of the revision of the 
    * current encoded state, i.e., `newRevision == currentRevision + 1`. This ensures that there is no collision with 
    * persistence IDs that have accidentally been used multiple times.
    * 
    * @tparam S
    *   The state's payload type
    * @param persistenceId
    *   The [[PersistenceId]] of the durable state instance to write
    * @param state
    *   The [[DurableState]] of payload type `S`
    * @param payloadCodec 
    *   a given [[PayloadCodec]] to convert [[DurableState]] instances of payload type `S` to a byte array and vice versa
    * @return
    *   An `IO[Unit]`
    */
  def write[S](persistenceId: PersistenceId, state: DurableState[S])(using payloadCodec: PayloadCodec[S]): IO[Unit] = 
    for
      encodedPayload   <- payloadCodec.encode(state.payload)
      encodedState      = EncodedState(payload = encodedPayload, 
                                       revision = state.revision, 
                                       timestamp = state.timestamp
                                      )
      _                <- writeEncodedState(persistenceId, encodedState)
    yield ()

end DurableStateStore


object DurableStateStore:
  
  sealed trait Error extends Exception
  final case class RevisionMismatchError(
    persistenceId: PersistenceId, 
    expectedRevision: Long, 
    actualRevision: Long
  ) extends Error

  def make(): IO[Resource[IO, DurableStateStore]] = 
    for
      config <- Config.default()
      store  <- make(config)
    yield store

  def make(config: Config): IO[Resource[IO, DurableStateStore]] =
    for
      persistenceConfig  <- IO.fromOption(config.peloton.persistence)(new IllegalArgumentException("Invalid peloton config: no persistence section found.")) 
      driver             <- Driver(persistenceConfig.driver)
      store              <- driver.createDurableStateStore(persistenceConfig)
    yield store

  def use[A](f: DurableStateStore ?=> IO[A]): IO[A] = 
    for
      store  <- make()
      retval <- store.use { case given DurableStateStore => f }
    yield retval

  def use[A](config: Config)(f: DurableStateStore ?=> IO[A]): IO[A] = 
    for
      store  <- make(config)
      retval <- store.use { case given DurableStateStore => f }
    yield retval

end DurableStateStore