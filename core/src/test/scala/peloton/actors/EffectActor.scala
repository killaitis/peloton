package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*
import peloton.actor.Behavior

import cats.effect.*

/**
  * An Actor that runs an effect from the command handler and send a message to itself after completion
  */
object EffectActor:

  final case class State(meaningOfLife: Option[Int] = None)

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

  given CanAsk[Message.Run,    Response.RunResponse]    = canAsk
  given CanAsk[Message.Cancel, Response.CancelResponse] = canAsk
  given CanAsk[Message.Get,    Response.GetResponse]    = canAsk

  def spawn(name: String = "EffectActor", effect: IO[Int])(using actorSystem: ActorSystem) = 

    def behaviorWhenIdle: Behavior[State, Message] = 
      (state, message, context) => message match
        case Message.Run() => 
          for
            fiber  <- context.pipeToSelf(effect):
                        case Outcome.Canceled() | Outcome.Errored(_) => 
                          IO.pure(List(Message.HandleResult(None)))
                        case Outcome.Succeeded(mol) => 
                          mol.map(mol => List(Message.HandleResult(Some(mol))))
            _      <- context.reply(Response.RunResponse(effectStarted = true))
          yield behaviorWhenActive(fiber)

        case Message.Cancel() => 
          context.reply(Response.CancelResponse(effectCancelled = false))

        case Message.Get() => 
          context.reply(Response.GetResponse(meaningOfLife = state.meaningOfLife))

        case Message.HandleResult(meaningOfLife) => 
          context.currentBehaviorM
    end behaviorWhenIdle

    def behaviorWhenActive(fiber: FiberIO[Unit]): Behavior[State, Message] = 
      (state, message, context) => message match
        case Message.Run() => 
          context.reply(Response.RunResponse(effectStarted = false))

        case Message.Cancel() => 
          for
            _  <- fiber.cancel
            _  <- context.reply(Response.CancelResponse(effectCancelled = true))
          yield behaviorWhenIdle

        case Message.Get() => 
          context.reply(Response.GetResponse(meaningOfLife = state.meaningOfLife))

        case Message.HandleResult(meaningOfLife) => 
          for
            _ <- context.setState(State(meaningOfLife = meaningOfLife))
          yield behaviorWhenIdle
    end behaviorWhenActive

    actorSystem.spawnActor[State, Message](
      name            = Some(name),
      initialState    = State(),
      initialBehavior = behaviorWhenIdle
    )
  end spawn

end EffectActor
