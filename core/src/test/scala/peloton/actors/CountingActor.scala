package peloton.actors

import cats.effect.IO
import cats.effect.Ref

import peloton.actor.Actor
import peloton.actor.ActorRef
import peloton.actor.Actor.*
import peloton.persistence.PersistenceId
import peloton.persistence.DurableStateStore
import peloton.persistence.PayloadCodec
import peloton.actor.ActorSystem
import peloton.persistence.JsonPayloadCodec

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

  sealed trait Command

  case object Open extends Command
  case object OpenResponse
  given CanAsk[Open.type, OpenResponse.type]  = canAsk
  
  case object Close extends Command
  case object CloseResponse
  given CanAsk[Close.type, CloseResponse.type] = canAsk
  
  case object Inc extends Command
  
  case object GetState extends Command
  case class GetStateResponse(isOpen: Boolean, counter: Int)
  given CanAsk[GetState.type, GetStateResponse] = canAsk
  
  case object Fail extends Command
  case class FailResponse()
  given CanAsk[Fail.type, FailResponse] = canAsk

  case object CountingException extends Exception

  // The persistent state
  case class State(counter: Int = 0)
  given PayloadCodec[State] = JsonPayloadCodec.create

  // The non-persistent state
  case class NPState(isOpen: Boolean = false)

  def spawn(persistenceId: PersistenceId, name: String = "CountingActor")(using DurableStateStore)(using actorSystem: ActorSystem): IO[ActorRef[Command]] =
    for
      npStateRef   <- Ref.of[IO, NPState](NPState())
      actor        <- actorSystem.spawn[State, Command](
                        name            = name,
                        persistenceId   = persistenceId, 
                        initialState    = State(), 
                        initialBehavior = 
                          (state, message, context) => 
                            message match
                              case Open => 
                                npStateRef.set(NPState(isOpen = true)) >>
                                context.respond(OpenResponse) >> 
                                context.unstashAll()

                              case Close => 
                                npStateRef.set(NPState(isOpen = false)) >>
                                context.respond(CloseResponse)

                              case Inc =>
                                for
                                  npState  <- npStateRef.get
                                  _        <- if npState.isOpen then context.setState(State(counter = state.counter + 1))
                                              else context.stash()
                                yield context.currentBehavior

                              case GetState =>
                                for
                                  npState  <- npStateRef.get
                                  _        <- context.respond(GetStateResponse(isOpen = npState.isOpen, counter = state.counter))
                                yield context.currentBehavior

                              case Fail => 
                                IO.raiseError(CountingException)
                      )
    yield actor
  