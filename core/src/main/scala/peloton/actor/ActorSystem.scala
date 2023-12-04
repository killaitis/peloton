package peloton.actor

import cats.effect.*
import cats.effect.std.AtomicCell
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import scala.concurrent.duration.Duration

import peloton.actor.Actor.CanAsk
import peloton.actor.kernel.*
import peloton.utils.*
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.DurableStateStore

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
    * Tries to obtain the ActorRef for a given actor name
    *
    * @param name
    * @param ct
    * @return
    */
  def actorRef[M](name: String)(using ct: reflect.ClassTag[M]): IO[Option[ActorRef[M]]] =
    for 
      refs <- actorRefs.get
      ref   = refs.get(name).filter(r => r.classTag.equals(ct)).map(_.asInstanceOf[ActorRef[M]])
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
                      override def ask[M2 <: M, R](message: M2, timeout: Duration = Duration.Inf)(using CanAsk[M2, R]): IO[R] = actor.ask(message)
                      override def terminate: IO[Unit] = 
                        actorRefs.evalUpdate: refs =>
                          for
                            _ <- if refs.contains(name) then actor.terminate else IO.unit
                          yield (refs - name)
                    }
      yield (refs + (name -> actorRef), actorRef)

end ActorSystem

object ActorSystem:
  def apply(): Resource[IO, ActorSystem] = 
    val acquire = 
      for 
        actorRefs    <- AtomicCell[IO].of(Map.empty[String, ActorRef[?]])
        actorSystem   = new ActorSystem(actorRefs)
      yield 
        actorSystem

    Resource.make(acquire)(_.shutdown)
    
end ActorSystem