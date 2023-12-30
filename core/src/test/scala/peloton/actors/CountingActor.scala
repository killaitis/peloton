package peloton.actors

import cats.effect.IO
import cats.effect.Ref

import peloton.actor.Actor
import peloton.actor.ActorRef
import peloton.actor.Actor.*
import peloton.actor.ActorSystem
import peloton.persistence.PersistenceId
import peloton.persistence.DurableStateStore
import peloton.persistence.PayloadCodec
import peloton.persistence.KryoPayloadCodec
import peloton.actor.Behavior

/**
  * A simple actor that 
  * - has a counter as its persistent state
  * - has a gate flag as its non-persistent state
  * - the gate flag can be set by sending an Open or Close message.
  * - the counter can be incremented by sending an Inc message.
  * - the counter can only be modified if the gate is open. If the gate is closed, Inc messages will be pushed to the actor's message stash
  * - the counter value can be retrieved by sending a Get message
  */
object CountingActor:

  // The persistent state
  final case class State(counter: Int = 0)

  // The non-persistent state
  final case class NPState(isOpen: Boolean = false)

  // Commands (messages) and responses
  sealed trait Command
  object Command:
    case object Open extends Command
    case object Close extends Command
    case object Inc extends Command
    case object GetState extends Command
    case object Fail extends Command

  object Response:
    case object OpenResponse
    case object CloseResponse
    final case class GetStateResponse(isOpen: Boolean, counter: Int)
    final case class FailResponse()

  given CanAsk[Command.Open.type,     Response.OpenResponse.type]   = canAsk
  given CanAsk[Command.Close.type,    Response.CloseResponse.type]  = canAsk
  given CanAsk[Command.GetState.type, Response.GetStateResponse]    = canAsk
  given CanAsk[Command.Fail.type,     Response.FailResponse]        = canAsk

  private given PayloadCodec[State] = KryoPayloadCodec.create

  case object CountingException extends Exception

  def spawn
        (persistenceId: PersistenceId, name: String = "CountingActor")
        (using DurableStateStore)
        (using actorSystem: ActorSystem): IO[ActorRef[Command]] =
    for
      npStateRef   <- Ref.of[IO, NPState](NPState())
      actor        <- actorSystem.spawnDurableStateActor[State, Command](
                        name            = Some(name),
                        persistenceId   = persistenceId, 
                        initialState    = State(), 
                        initialBehavior = (state, message, context) => 
                          message match
                            case Command.Open => 
                              npStateRef.set(NPState(isOpen = true)) >>
                              context.reply(Response.OpenResponse) >> 
                              context.unstashAll()

                            case Command.Close => 
                              npStateRef.set(NPState(isOpen = false)) >>
                              context.reply(Response.CloseResponse)

                            case Command.Inc =>
                              for
                                npState  <- npStateRef.get
                                _        <- if npState.isOpen 
                                            then context.setState(State(counter = state.counter + 1))
                                            else context.stash()
                              yield context.currentBehavior

                            case Command.GetState =>
                              for
                                npState  <- npStateRef.get
                                _        <- context.reply(Response.GetStateResponse(isOpen = npState.isOpen, counter = state.counter))
                              yield context.currentBehavior

                            case Command.Fail => 
                              IO.raiseError(CountingException)
                      )
    yield actor
  