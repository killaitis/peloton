package peloton.actor

import peloton.actor.Actor.CanAsk
import peloton.actor.internal.{DurableStateActor, StatefulActor, EventSourcedActor}
import peloton.http.ActorSystemServer
import peloton.http.RemoteActorRef
import peloton.config.Config
import peloton.utils.assert
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.DurableStateStore
import peloton.persistence.EventStore
import peloton.persistence.Retention
import peloton.persistence.MessageHandler
import peloton.persistence.EventHandler
import peloton.persistence.SnapshotPredicate

import cats.effect.*
import cats.implicits.*
import cats.effect.std.AtomicCell
import cats.effect.std.UUIDGen

import com.comcast.ip4s.*

import scala.concurrent.duration.*
import scala.reflect.ClassTag
import java.net.URI


class ActorSystem private (actorRefs: AtomicCell[IO, Map[String, ActorRef[?]]]):

  /**
    * Spawn a new [[Actor]] with simple, stateful behavior. 
    * 
    * The actor maintains an internal state which is passed to the message handler an can be updated using
    * the context's [[Context.setState]] method. The state is kept only in memory and is not persisted in 
    * any way.
    *
    * @tparam S 
    *   The type of the actor's internal state
    * @tparam M
    *   The actor's message type
    * @param initialState
    *   The initial state for the actor.
    * @param initialBehavior
    *   The initial behavior, i.e., the message handler function. The function takes the current state of the actor, 
    *   the input (message) and the actor's [[Context]] as parameters and returns a new [[Behavior]], depending on 
    *   the state and the message. The behavior is evaluated effectful.
    * @param name
    *   An optional name for the actor. If omitted, the actor system will create a name for the actor. 
    * @param ClassTag
    *   A given instance of a [[ClassTag]] for the actor's message type `M`. Used internally to circumvent the
    *   JVM's type limitations.
    * @return
    *   An `IO` of [[ActorRef]]
    */
  def spawnActor[S, M](initialState: S,
                  initialBehavior: Behavior[S, M],
                  name: Option[String] = None
                 )(using ClassTag[M]): IO[ActorRef[M]] = 
    register(StatefulActor.spawn[S, M](initialState, initialBehavior), 
             name
            )

  /**
    * Spawns a new [[Actor]] with a durable (persistent) state.
    *
    * The `DurableStateActor` is connected to a given [[DurableStateStore]] instance. A given `PersistenceId` 
    * connects this actor instance to a distinct record in the state store, e.g., database row with index key.
    * 
    * When the actor spawns, the last known previous actor state is read from the store and used as the 
    * initial state of the actor. If no previous state was found, a given default state is used.
    * 
    * While processing incoming messages, the actor's Behavior can use [[Context.setState]] to update the 
    * state's representation in the `DurableStateStore`.
    *
    * @tparam S 
    *   The type of the actor's internal state
    * @tparam M
    *   The actor's message type
    * @param persistenceId
    *   A unique identifier of type [[PersistenceId]] that references the persisted state of this actor in the [[DurableStateStore]].
    * @param initialState
    *   A default/initial state that is used if the actor has never stored its state with the given `persistenceId`.
    * @param initialBehavior
    *   The initial behavior, i.e., the message handler function. The function takes the current state of the actor, 
    *   the input (message) and the actor's [[Context]] as parameters and returns a new [[Behavior]], depending on 
    *   the state and the message. The behavior is evaluated effectful.
    * @param name
    *   An optional name for the actor. If omitted, the actor system will create a name for the actor. 
    * @param DurableStateStore
    *   A given instance of [[DurableStateStore]]
    * @param PayloadCodec
    *   A given instance of a [[PayloadCodec]] for the actor's state type `S`. Used to convert the state to a byte array and vice versa.
    * @param ClassTag
    *   A given instance of a [[ClassTag]] for the actor's message type `M`. Used internally to circumvent the
    *   JVM's type limitations.
    * @return
    *   An `IO` of [[ActorRef]]
    */
  def spawnDurableStateActor[S, M](persistenceId: PersistenceId,
                                   initialState: S,
                                   initialBehavior: Behavior[S, M],
                                   name: Option[String] = None
                                  )(using 
                                   DurableStateStore,
                                   PayloadCodec[S],
                                   ClassTag[M]
                                  ): IO[ActorRef[M]] =
    register(DurableStateActor.spawn[S, M](persistenceId, 
                                           initialState, 
                                           initialBehavior
                                          ), 
             name
            )

  /**
    * Spawn a new [[Actor]] with event sourced behavior. 
    * 
    * The message processing of the event sourced actor consists of two phases:
    * 
    * 1. the message handler: 
    *   - accepts the incoming messages of type `M`
    *   - uses the provided actor context to possibly reply to the sender (e.g. in case of an ASK pattern)
    *   - perform other actions that *do not* modify the state of the actor
    *   - returns an [[EventAction]] that determines if the message has to be converted into an event (which 
    *     is then written to the event store)
    * 
    * 2. the event handler:
    *   - accepts incoming event (from the message handler or the event store)
    *   - implements the business logic to modify the actor state
    *   - returns the new state
    * 
    * So, in short, the message handler is mainly responsible to convert messages to events (which are then 
    * written to the event store) while the event handler is responsible to update the actor state.
    * 
    * On start, an event sourced actor will read all existing events (for the given persistence ID) from 
    * the event store and replay them using the event handler. This leaves the actor in the same state
    * as before the last shutdown.
    * 
    * Unlike other actor types, the event sourced actor is not allowed to change its behavior. It will always
    * use a behavior of type [[EventSourcedBehavior]]
    *
    * @tparam S 
    *   The type of the actor's internal state
    * @tparam M
    *   The actor's message type
    * @tparam E
    *   The actor's event type
    * @param persistenceId
    *   A unique identifier of type [[PersistenceId]] that references the persisted state of this actor in the [[EventStore]].
    * @param initialState
    *   The initial state for the actor.
    * @param messageHandler
    *   The message handler of type [[MessageHandler]]
    * @param eventHandler
    *   The event handler of type [[EventHandler]]
    * @param snapshotPredicate
    *   A function that takes the current state of the actor (after applying the current event), the current event and 
    *   the number of events that have been processed (icluding the current event) since the last snapshot. The function
    *   returns a Boolean that indicates if a new snapshot needs to be created.
    * @retention
    *   [[Retention]] parameters that control if and how events and snapshots are purged after creating a new snapshot. 
    * @param name
    *   An optional name for the actor. If omitted, the actor system will create a name for the actor. 
    * @param EventStore
    *   A given instance of [[EventStore]]
    * @param PayloadCodec
    *   A given instance of a [[PayloadCodec]] for the actor's event type `E`. Used to convert events to a byte array and vice versa.
    * @param PayloadCodec
    *   A given instance of a [[PayloadCodec]] for the actor's state type `S`. Used to convert the state to a byte array and vice versa.
    * @param ClassTag
    *   A given instance of a [[ClassTag]] for the actor's message type `M`. Used internally to circumvent the
    *   JVM's type limitations.
    * @return
    *   An `IO` of [[ActorRef]]
    */
  def spawnEventSourcedActor[S, M, E](persistenceId: PersistenceId,
                                      initialState: S,
                                      messageHandler: MessageHandler[S, M, E],
                                      eventHandler: EventHandler[S, E],
                                      snapshotPredicate: SnapshotPredicate[S, E] = SnapshotPredicate.noSnapshots,
                                      retention: Retention = Retention(),
                                      name: Option[String] = None
                                     )(using 
                                      EventStore,
                                      PayloadCodec[E],
                                      PayloadCodec[S],
                                      ClassTag[M]
                                     ): IO[ActorRef[M]] =
    register(EventSourcedActor.spawn[S, M, E](persistenceId, 
                                              initialState, 
                                              messageHandler, 
                                              eventHandler, 
                                              snapshotPredicate,
                                              retention
                                             ), 
             name
            )

  /**
    * Obtains the [[ActorRef]] to a local actor in this actor system.
    *
    * @param name
    *   The unique name of the actor in this actor system. 
    * @param ClassTag
    *   A given instance of a [[ClassTag]] for the actor's message type `M`. Used internally to circumvent the
    *   JVM's type limitations.
    * @return
    *   An `IO` of [[ActorRef]] for the given actor name if it exists or a failed `IO` otherwise.
    */
  def actorRef[M](name: String)(using ct: ClassTag[M]): IO[ActorRef[M]] =
    for 
      refs       <- actorRefs.get
      ref        <- IO.fromOption(refs.get(name))(new NoSuchElementException(s"actor not found: $name"))
      fromClass   = ref.classTag.runtimeClass
      toClass     = ct.runtimeClass 
      ref        <- if toClass.isAssignableFrom(fromClass) 
                    then IO.pure(ref) 
                    else IO.raiseError(new IllegalArgumentException(s"actor $name supports messages of type ${ref.classTag.runtimeClass.getName}, but not ${ct.runtimeClass.getName}"))
    yield ref.asInstanceOf[ActorRef[M]]

  /**
    * Obtains the [[ActorRef]] to a remote actor on a possibly different machine.
    *
    * @param uri
    *   A URI, referencing the actor on the remote actor system. Must have the format `peloton://<host>:<port>/<actor_name>`, 
    *   e.g., `peloton://192.168.100.6:5000/my_actor`.
    * @param ClassTag
    *   A given instance of a [[ClassTag]] for the actor's message type `M`. Used internally to circumvent the
    *   JVM's type limitations.
    * @return
    *   An `IO` of [[ActorRef]] for the given URI.
    */
  def remoteActorRef[M](uri: URI)(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] =
    for
      _          <- IO.raiseWhen(uri.getScheme != "peloton")(new IllegalArgumentException(s"unsupported URI scheme: ${uri.getScheme}"))
      host        = uri.getHost
      port        = uri.getPort
      actorName   = uri.getPath.stripPrefix("/") // TODO: THIS IS DIRTY!!!
      ref        <- IO.pure(new RemoteActorRef[M](host = host, port = port, actorName = actorName))
    yield ref

  /**
    * Terminates an actor
    *
    * @param actorRef 
    *   A reference to the actor to terminate
    * @return 
    *   `IO[Unit]`
    */
  def terminate(actorRef: ActorRef[?]): IO[Unit] = 
    actorRef.terminate

  /**
    * Shuts down the actor system.
    * 
    * This will also terminate all running actors. If an actor is currently processing a message, 
    * termination will wait for the message handler to finish.
    *
    * @return
    *   `IO[Unit]`
    */
  def shutdown: IO[Unit] = 
    for 
      refs <- actorRefs.get
      _    <- refs.values.toList.traverse_(_.terminate)
    yield ()

  /**
    * Internal implementation for registering a new actor in the actor system
    *
    * @param spawnF
    *   A by-name parameter of an effect that, on evaluation, will create the new actor.
    * @param maybeName
    *   An optional name for the actor. The name has to be unique inside of the actor system. 
    *   The function will fail if an actor is already registered with this name. If the parameter 
    *   is omitted or set to `None`, a random name will be generated.
    * @param ct
    *   A given `ClassTag` of the actor's message type `M`
    * @return 
    *   An `IO` that evaluates to an `ActorRef` to the new actor.
    */
  private def register[M](spawnF: => IO[Actor[M]], 
                          maybeName: Option[String] = None
                         )(using 
                          ct: reflect.ClassTag[M]
                         ): IO[ActorRef[M]] = 
    actorRefs.evalModify: refs =>

      def uniqueActorName: IO[String] = 
        for 
          nameProposal <- UUIDGen.randomString[IO]
          name         <- if refs.contains(nameProposal) 
                          then uniqueActorName 
                          else IO.pure(nameProposal)
        yield name

      for
        actorName  <- maybeName match
                        case Some(name) => IO.pure(name)
                        case None       => uniqueActorName
        _          <- IO.assert(!refs.contains(actorName))(IllegalArgumentException(s"An actor with name '$actorName' already exists!"))
        actor      <- spawnF
        actorRef    = new ActorRef[M]:
                        override def name = actorName
                        override def classTag = ct
                        override def tell(message: M): IO[Unit] = actor.tell(message)
                        override def ask[M2 <: M, R](message: M2, timeout: FiniteDuration)(using CanAsk[M2, R]): IO[R] = actor.ask(message)
                        override def terminate: IO[Unit] = 
                          actorRefs.evalUpdate: refs =>
                            for
                              _ <- if refs.contains(actorName) then actor.terminate else IO.unit
                            yield (refs - actorName)

      yield (refs + (actorName -> actorRef), actorRef)
  end register

end ActorSystem


object ActorSystem:

  def make(): IO[Resource[IO, ActorSystem]] = 
    for 
      config       <- Config.default[IO]()
      actorSystem  <- ActorSystem.make(config)
    yield actorSystem

  def make(config: Config): IO[Resource[IO, ActorSystem]] = 
    for
        httpServerRef    <- Ref[IO].of[Option[FiberIO[Nothing]]](None)
        acquire           = 
                            for 
                              // Create a new ActorSystem
                              actorRefs    <- AtomicCell[IO].of(Map.empty[String, ActorRef[?]])
                              actorSystem   = new ActorSystem(actorRefs)

                              // Start HTTP Server if activated in the config
                              _            <- config.peloton.http.traverse_ { http => 
                                                for 
                                                  host       <- IO.fromOption(Hostname.fromString(http.hostname))(new IllegalArgumentException(s"Invalid hostname: ${http.hostname}"))
                                                  port       <- IO.fromOption(Port.fromInt(http.port))(new IllegalArgumentException(s"Invalid port: ${http.port}"))
                                                  httpServer  = ActorSystemServer(host, port, actorSystem)
                                                  fib        <- httpServer.use(_ => IO.never).start
                                                  _          <- httpServerRef.set(Some(fib))
                                                yield ()
                                              }
                            yield actorSystem
        release           = (actorSystem: ActorSystem) =>
                              for 
                                maybeHttpServer  <- httpServerRef.get
                                _                <- maybeHttpServer.traverse_(_.cancel)
                                _                <- actorSystem.shutdown
                              yield ()

    yield Resource.make(acquire)(release)

  /**
   * Wraps a given function into an [[ActorSystem]] resource bracket, 
   * i.e., creates an actor system, applies the function and releases 
   * the actor system.
   * 
   * The actor system will be created using the Peloton default configuration.
   * 
   * @param f
   *   Á function that takes an implicit/given instance of [[ActorSystem]] 
   *   as argument and returns an `IO` of any type `A`.
   */
  def use[A](f: ActorSystem ?=> IO[A]): IO[A] = 
    for
      actorSystemRes <- ActorSystem.make()
      retval         <- actorSystemRes.use { case given ActorSystem => f }
    yield retval

  /**
   * Wraps a given function into an [[ActorSystem]] resource bracket, 
   * i.e., creates an actor system, applies the function and releases 
   * the actor system.
   * 
   * @param config
   *   The Peloton configuration used to create the actor system.
   * @param f
   *   Á function that takes an implicit/given instance of [[ActorSystem]] 
   *   as argument and returns an `IO` of any type `A`.
   */
  def use[A](config: Config)(f: ActorSystem ?=> IO[A]): IO[A] = 
    for
      actorSystemRes <- ActorSystem.make(config)
      retval         <- actorSystemRes.use { case given ActorSystem => f }
    yield retval

end ActorSystem