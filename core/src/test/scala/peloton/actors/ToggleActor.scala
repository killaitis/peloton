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
  object Message:
    final case class Toggle() extends Message
    final case class GetMode() extends Message
    
  object Response:
    final case class ToggleResponse()
    final case class GetModeResponse(mode: Mode)

  given CanAsk[Message.Toggle,  Response.ToggleResponse]  = canAsk
  given CanAsk[Message.GetMode, Response.GetModeResponse] = canAsk

  private val behaviorA: Behavior[Unit, Message] = (_, message, context) => message match
    case Message.Toggle() => 
      context.reply(Response.ToggleResponse()) >> 
      behaviorB.pure

    case Message.GetMode() =>
      context.reply(Response.GetModeResponse(Mode.ModeA))

  private val behaviorB: Behavior[Unit, Message] = (_, message, context) => message match
    case Message.Toggle() => 
      context.reply(Response.ToggleResponse()) >> 
      behaviorA.pure

    case Message.GetMode() =>
      context.reply(Response.GetModeResponse(Mode.ModeB))

  def spawn(name: String)(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[Unit, Message](
      name            = Some(name),
      initialState    = (),
      initialBehavior = behaviorA
    )
