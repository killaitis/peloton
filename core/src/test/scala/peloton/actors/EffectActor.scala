package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*

import cats.effect.*

/**
  * An Actor that runs an effect from the command handler and send a message to itself after completion
  */
object EffectActor:

  sealed trait Command

  final case class Run() extends Command
  final case class RunResponse(effectStarted: Boolean)
  given CanAsk[Run, RunResponse] = canAsk
  
  final case class Cancel() extends Command
  final case class CancelResponse(effectCancelled: Boolean)
  given CanAsk[Cancel, CancelResponse] = canAsk
  
  final case class Get() extends Command
  final case class GetResponse(meaningOfLife: Option[Int])
  given CanAsk[Get, GetResponse] = canAsk

  final case class HandleResult(mol: Option[Int]) extends Command
  final case class HandleResultResponse()
  given CanAsk[HandleResult, HandleResultResponse] = canAsk

  case class State(
    runningFiber: Option[FiberIO[Unit]] = None, 
    meaningOfLife: Option[Int] = None
  )

  def spawn(name: String = "EffectActor", effect: IO[Int])(using actorSystem: ActorSystem) = 
    actorSystem.spawn[State, Command](
      name = name,
      initialState = State(),
      initialBehavior = (state, message, context) => message match
        case Run() => 
          state.runningFiber match
            case None => // no effect is currently running
              for
                fiber  <- context.pipeToSelf(effect):
                            case Outcome.Canceled() | Outcome.Errored(_) => 
                              IO.pure(List(HandleResult(None)))
                            case Outcome.Succeeded(mol) => 
                              mol.map(mol => List(HandleResult(Some(mol))))
                _      <- context.setState(State(runningFiber = Some(fiber)))
                _      <- context.reply(RunResponse(effectStarted = true))
              yield context.currentBehavior
            case Some(fiber) => // effect is already running
              context.reply(RunResponse(effectStarted = false))

        case Cancel() => 
          state.runningFiber match
            case None => // no effect is currently running
              context.reply(CancelResponse(effectCancelled = false))
            case Some(fiber) => // effect is already running
              for
                _  <- fiber.cancel
                _  <- context.setState(State(runningFiber = None))
                _  <- context.reply(CancelResponse(effectCancelled = true))
              yield context.currentBehavior

        case Get() => 
          context.reply(GetResponse(meaningOfLife = state.meaningOfLife))

        case HandleResult(meaningOfLife) => 
            context.setState(State(runningFiber = None, meaningOfLife = meaningOfLife)) >> 
            context.reply(HandleResultResponse())
    )
