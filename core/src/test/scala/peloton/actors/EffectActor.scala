package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*

import cats.effect.*

/**
  * An Actor that runs an effect from the command handler and send a message to itself after completion
  */
object EffectActor:

  case class State(
    runningFiber: Option[FiberIO[Unit]] = None, 
    meaningOfLife: Option[Int] = None
  )

  sealed trait Message
  object Message:
    final case class Run() extends Message
    final case class Cancel() extends Message
    final case class Get() extends Message
    final case class HandleResult(mol: Option[Int]) extends Message

  object Response:
    final case class RunResponse(effectStarted: Boolean)
    final case class CancelResponse(effectCancelled: Boolean)
    final case class GetResponse(meaningOfLife: Option[Int])
    final case class HandleResultResponse()

  given CanAsk[Message.Run,           Response.RunResponse]          = canAsk
  given CanAsk[Message.Cancel,        Response.CancelResponse]       = canAsk
  given CanAsk[Message.Get,           Response.GetResponse]          = canAsk
  given CanAsk[Message.HandleResult,  Response.HandleResultResponse] = canAsk

  def spawn(name: String = "EffectActor", effect: IO[Int])(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[State, Message](
      name = Some(name),
      initialState = State(),
      initialBehavior = (state, message, context) => message match
        case Message.Run() => 
          state.runningFiber match
            case None => // no effect is currently running
              for
                fiber  <- context.pipeToSelf(effect):
                            case Outcome.Canceled() | Outcome.Errored(_) => 
                              IO.pure(List(Message.HandleResult(None)))
                            case Outcome.Succeeded(mol) => 
                              mol.map(mol => List(Message.HandleResult(Some(mol))))
                _      <- context.setState(State(runningFiber = Some(fiber)))
                _      <- context.reply(Response.RunResponse(effectStarted = true))
              yield context.currentBehavior
            case Some(fiber) => // effect is already running
              context.reply(Response.RunResponse(effectStarted = false))

        case Message.Cancel() => 
          state.runningFiber match
            case None => // no effect is currently running
              context.reply(Response.CancelResponse(effectCancelled = false))
            case Some(fiber) => // effect is already running
              for
                _  <- fiber.cancel
                _  <- context.setState(State(runningFiber = None))
                _  <- context.reply(Response.CancelResponse(effectCancelled = true))
              yield context.currentBehavior

        case Message.Get() => 
          context.reply(Response.GetResponse(meaningOfLife = state.meaningOfLife))

        case Message.HandleResult(meaningOfLife) => 
            context.setState(State(runningFiber = None, meaningOfLife = meaningOfLife)) >> 
            context.reply(Response.HandleResultResponse())
    )
