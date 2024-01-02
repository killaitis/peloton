package peloton.actor

import peloton.actor.Actor.CanAsk

import cats.effect.*

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import java.net.URI

trait ActorRef[M]:

  def name: String
  
  def classTag: ClassTag[M] // TODO: this is pretty bad and should be replaced (perhaps with scala.quoted.Type)

  def tell(message: M): IO[Unit]
  def ask[M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R]
  def terminate: IO[Unit]

  inline def ! (message: M): IO[Unit] = tell(message)
  inline def ? [M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R] = ask[M2, R](message, timeout)
end ActorRef

object ActorRef:
  def of[M](actorName: String)(using ct: reflect.ClassTag[M])(using actorSystem: ActorSystem): IO[ActorRef[M]] =
    actorSystem.actorRef(actorName)

  def of[M](uri: URI)(using ct: reflect.ClassTag[M])(using actorSystem: ActorSystem): IO[ActorRef[M]] =
    actorSystem.remoteActorRef(uri)
end ActorRef
