package peloton.actor

import peloton.actor.Actor.CanAsk
import peloton.http.ActorSystemServer
import peloton.actor.kernel.*
import peloton.utils.*
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.DurableStateStore
import peloton.config.Config
import peloton.http.RemoteActorRef

import cats.effect.*
import cats.implicits.*
import cats.effect.std.AtomicCell
import cats.effect.std.UUIDGen
import com.comcast.ip4s.*

import scala.concurrent.duration.*
import java.net.URI
import scala.reflect.ClassTag

class ActorSystem private (actorRefs: AtomicCell[IO, Map[String, ActorRef[?]]]):

  /**
    * Spawns a stateful actor
    *
    * @param initialState
    * @param initialBehavior
    * @param name
    * @return
    */
  def spawn[S, M](initialState: S,
                  initialBehavior: Behavior[S, M],
                  name: String
                 )(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] = 
    spawn(Some(name), StatefulActor.spawn[S, M](initialState, initialBehavior))

  /**
    * Spawns a stateful actor
    *
    * @param initialState
    * @param initialBehavior
    * @return
    */
  def spawn[S, M](initialState: S,
                  initialBehavior: Behavior[S, M],
                 )(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] = 
    spawn(None, StatefulActor.spawn[S, M](initialState, initialBehavior))

  /**
    * Spawns a persistent actor
    *
    * @param persistenceId
    * @param initialState
    * @param initialBehavior
    * @param name
    * @param codec
    * @param store
    * @return
    */
  def spawn[S, M](persistenceId: PersistenceId,
                  initialState: S,
                  initialBehavior: Behavior[S, M],
                  name: String
                 )(using 
                  codec: PayloadCodec[S],
                  store: DurableStateStore,
                  ct: reflect.ClassTag[M]
                 ): IO[ActorRef[M]] =
    spawn(Some(name), PersistentActor.spawn[S, M](persistenceId, initialState, initialBehavior))

  /**
    * Spawns a persistent actor
    *
    * @param persistenceId
    * @param initialState
    * @param initialBehavior
    * @param codec
    * @param store
    * @return
    */
  def spawn[S, M](persistenceId: PersistenceId,
                  initialState: S,
                  initialBehavior: Behavior[S, M]
                 )(using 
                  codec: PayloadCodec[S],
                  store: DurableStateStore,
                  ct: reflect.ClassTag[M]
                 ): IO[ActorRef[M]] =
    spawn(None, PersistentActor.spawn[S, M](persistenceId, initialState, initialBehavior))

  /**
    * Obtains the ActorRef for a given actor name
    *
    * @param name
    * @param ct
    * @return
    */
  def actorRef[M](name: String)(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] =
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
    * Obtains the ActorRef for a given remote actor URL
    *
    * @param uri
    * @param ct
    * @return
    */
  def remoteActorRef[M](uri: URI)(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] =
    for
      _   <- IO.raiseWhen(uri.getScheme != "peloton")(new IllegalArgumentException(s"unsupported URI scheme: ${uri.getScheme}"))
      host = uri.getHost
      port = uri.getPort
      actorName = uri.getPath.stripPrefix("/") // TODO: THIS IS DIRTY!!!
      ref <- IO.pure(new RemoteActorRef[M](host = host, port = port, actorName = actorName))
    yield ref

  /**
    * Terminates an actor
    *
    * @param actorRef A reference to the actor to terminate
    * @return `IO[Unit]`
    */
  def terminate(actorRef: ActorRef[?]): IO[Unit] = 
    actorRef.terminate

  /**
    * Shuts down the actor system.
    *
    * @return `IO[Unit]`
    */
  def shutdown: IO[Unit] = 
    for 
      refs <- actorRefs.get
      _    <- refs.values.toList.traverse_(_.terminate)
    yield ()

  private def spawn[M](maybeName: Option[String], spawnF: => IO[Actor[M]])(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] = 
    actorRefs.evalModify: refs =>
      for
        name     <- maybeName match
                      case Some(name) => IO.pure(name)
                      case None       => UUIDGen.randomString[IO]
        _        <- IO.assert(!refs.contains(name))(s"An actor with name '$name' already exists!")
        actor    <- spawnF
        actorRef  = new ActorRef[M] {
                      override def classTag = ct
                      override def tell(message: M): IO[Unit] = actor.tell(message)
                      override def ask[M2 <: M, R](message: M2, timeout: FiniteDuration)(using CanAsk[M2, R]): IO[R] = actor.ask(message)
                      override def terminate: IO[Unit] = 
                        actorRefs.evalUpdate: refs =>
                          for
                            _ <- if refs.contains(name) then actor.terminate else IO.unit
                          yield (refs - name)
                    }
      yield (refs + (name -> actorRef), actorRef)

end ActorSystem

object ActorSystem:

  def make(): IO[Resource[IO, ActorSystem]] = 
    for 
      config       <- Config.default()
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

  def use[A](f: ActorSystem ?=> IO[A]): IO[A] = 
    for
      actorSystemRes <- ActorSystem.make()
      retval         <- actorSystemRes.use { case given ActorSystem => f }
    yield retval

  def use[A](config: Config)(f: ActorSystem ?=> IO[A]): IO[A] = 
    for
      actorSystemRes <- ActorSystem.make(config)
      retval         <- actorSystemRes.use { case given ActorSystem => f }
    yield retval

end ActorSystem