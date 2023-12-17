package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*
import peloton.actor.Behavior

import cats.effect.IO
import cats.implicits.*

/**
  * An Actor that can toggle its behavior
  */
object ToggleActor:

  enum Mode:
    case ModeA
    case ModeB

  sealed trait Message

  final case class Toggle() extends Message
  final case class ToggleResponse()
  given CanAsk[Toggle, ToggleResponse] = canAsk

  final case class GetMode() extends Message
  final case class GetModeResponse(mode: Mode)
  given CanAsk[GetMode, GetModeResponse] = canAsk

  val behaviorA: Behavior[Unit, Message] = (_, message, context) => message match
    case Toggle() => 
      context.respond(ToggleResponse()) >> 
      behaviorB.pure

    case GetMode() =>
      context.respond(GetModeResponse(Mode.ModeA))

  val behaviorB: Behavior[Unit, Message] = (_, message, context) => message match
    case Toggle() => 
      context.respond(ToggleResponse()) >> 
      behaviorA.pure

    case GetMode() =>
      context.respond(GetModeResponse(Mode.ModeB))

  def spawn(name: String)(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Unit, Message](
      name            = name,
      initialState    = (),
      initialBehavior = behaviorA
    )
