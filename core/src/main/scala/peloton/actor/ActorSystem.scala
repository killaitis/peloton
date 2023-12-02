package peloton.actor

import cats.effect.*
import cats.syntax.all.*
import scala.concurrent.duration.Duration

import peloton.actor.Actor.CanAsk
import peloton.actor.kernel.*
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.DurableStateStore

/*
TODOS:
  - shutdown behavior:
  - actor lifetime / automatic termination
  - actor hierarchy: concept of child actors
  - remote actors
  - event sourcing actors
  - make actor name optional and generate one

*/

class ActorSystem private (actorRefs: Ref[IO, Map[String, ActorRef[?]]]):

  /**
    * Spawn a stateful actor
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
    for
      actor    <- StatefulActor.spawn[S, M](initialState, initialBehavior)
      actorRef <- register(name, actor)
    yield actorRef

  /**
    * Spawn a persistent actor
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
    for
      actor    <- PersistentActor.spawn[S, M](persistenceId, initialState, initialBehavior)
      actorRef <- register(name, actor)
    yield actorRef

  def actorRef[M](name: String)(using ct: reflect.ClassTag[M]): IO[Option[ActorRef[M]]] =
    for 
      refs <- actorRefs.get
      ref   = refs.get(name).filter(r => r.classTag.equals(ct)).map(_.asInstanceOf[ActorRef[M]])
      _    <- ref.traverse_ { r => IO.println(s"${r.classTag.toString()}") }
    yield ref

  private def register[S, M](name: String, actor: Actor[M])(using ct: reflect.ClassTag[M]): IO[ActorRef[M]] =
    for
      actorRef <- new ActorRef[M] {
                    override def classTag = ct
                    override def tell(message: M): IO[Unit] = actor.tell(message)
                    override def ask[M2 <: M, R](message: M2, timeout: Duration = Duration.Inf)(using CanAsk[M2, R]): IO[R] = actor.ask(message)
                    override def terminate: IO[Unit] = actorRefs.update(_ - name) >> actor.terminate
                  }.pure[IO]
      _        <- actorRefs.update(_ + (name -> actorRef))
    yield actorRef

  def terminate(actorRef: ActorRef[?]): IO[Unit] = 
    actorRef.terminate

  def shutdown: IO[Unit] = 
    for 
      refs <- actorRefs.get
      _    <- refs.values.toList.traverse_(_.terminate)
    yield ()

end ActorSystem

object ActorSystem:
  def apply(): Resource[IO, ActorSystem] = 
    val acquire = 
      for 
        actorRefs <- Ref.of[IO, Map[String, ActorRef[?]]](Map.empty[String, ActorRef[?]])
        actorSystem = new ActorSystem(actorRefs)
      yield 
        actorSystem

    Resource.make(acquire)(_.shutdown)
    

end ActorSystem