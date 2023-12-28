import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorContext
import peloton.actor.ActorSystem
import peloton.actor.EventAction
import peloton.persistence.EventStore
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.JsonPayloadCodec

import cats.effect.{IO, IOApp}
import peloton.actor.SnapshotPredicate
import peloton.persistence.Retention


// An actor that tracks body energy. Eating or drinking increases the 
// energy level, activy decreases it.
//
// To use event sourced actors, you have to define:
// 
// - A state. Unlike durable state actors where the state itself is stored
//   in a DurableStateStore, event sourced actors keeps its state in memory 
//   only, but persists all modifications to the state and replays them on the 
//   restart of the actor which will recreate the same state.
// 
// - A protocol. Like any actor, each event sourced actor has a client-facing
//   message protocol (TELL or ASK). Unlike durable state actors where the
//   state is directly modified by the processing of the incoming messages,
//   event sourced actors transform the incoming messages to a stream of 
//   events (by applying a message handler to the message). After persisting 
//   the events in the EventStore, an event handler is applied to each event.
//   It is the event handler which will then finally modify the state.
// 
// - A message handler. It is responsible for converting incoming messages to
//   (durable) events and replying to the client. The message handler decides
//   if the message will produce an event. This is mirrored in the message
//   handler's return type EventAction which has options to create an event 
//   or ignore the message.
// 
// - An event handler. Is applied to the event stream, coming from either the
//   message handler (during the actor's life time) or from the event store 
//   (after the (re)start of the actor). The event stream can be regarded as 
//   a kind of transaction log of modifications to the actor state.
// 
object EnergyTrackerActor:

  // --- STATE -----------------------------------------------------------------

  // The actor's state. We will add or substract a specific amount of energy 
  // on each incoming message.
  final case class State(energy: Double = 0.0)

  // --- PROTOCOL --------------------------------------------------------------
  
  // The message protocol. This is what the clients will be using to 
  // interact with the actor
  sealed trait Message

  final case class EatPizza(count: Int) extends Message
  final case class DrinkJuice(count: Int) extends Message
  final case class GoHiking(hours: Double) extends Message
  final case class DoWorkout(hours: Double) extends Message
  case object GetEnergy extends Message

  // The actor will respond with a message on all commands, containing 
  // the amout of energy that was added or substracted or the total amout 
  // of energy.
  final case class EnergyAddedResponse(energy: Double)
  final case class EnergyUsedResponse(energy: Double)
  final case class EnergyResponse(energy: Double)

  // Define the ASK patterns.
  given CanAsk[EatPizza, EnergyAddedResponse]    = canAsk
  given CanAsk[DrinkJuice, EnergyAddedResponse]  = canAsk
  given CanAsk[GoHiking, EnergyUsedResponse]     = canAsk
  given CanAsk[DoWorkout, EnergyUsedResponse]    = canAsk
  given CanAsk[GetEnergy.type, EnergyResponse]   = canAsk

  // --- EVENTS ----------------------------------------------------------------

  // The events which will modify the state. You can use different event types 
  // for your actor, but they have to been derived from a common base type.
  // For demonstration purposes, we will use two events to simply add or use 
  // energy.
  sealed trait Event
  
  final case class AddEnergy(amout: Double) extends Event
  final case class UseEnergy(amout: Double) extends Event
  
  // The event store needs a given instance of a PayloadCodec for our events
  // to (de)serialize them when reading from and writing to the event store.
  private given PayloadCodec[Event] = JsonPayloadCodec.create

  // The event store also needs a given instance of a PayloadCodec for our state
  // to (de)serialize it when creating or reading snapshots.
  private given PayloadCodec[State] = JsonPayloadCodec.create

  // --- MESSAGE HANDLER -------------------------------------------------------

  // The message handler has access to the current state of the actor and the actor
  // context. It can reply to the sender by using the actor context's reply method.
  // The message handler needs to return an EventAction.

  private def messageHandler(state: State, message: Message, context: ActorContext[State, Message]): IO[EventAction[Event]] =
    message match
      case EatPizza(count) =>
        for 
          energy <- IO.pure(3.6 * count)
          _      <- context.reply(EnergyAddedResponse(energy))
        yield EventAction.Persist(AddEnergy(energy))

      case DrinkJuice(count) =>
        for 
          energy <- IO.pure(1.1 * count)
          _      <- context.reply(EnergyAddedResponse(energy))
        yield EventAction.Persist(AddEnergy(energy))

      case GoHiking(hours) =>
        for 
          energy <- IO.pure(4.3 * hours)
          _      <- context.reply(EnergyUsedResponse(energy))
        yield EventAction.Persist(UseEnergy(energy))

      case DoWorkout(hours) =>
        for 
          energy <- IO.pure(3.2 * hours)
          _      <- context.reply(EnergyUsedResponse(energy))
        yield EventAction.Persist(UseEnergy(energy))

      case GetEnergy =>
        context.reply(EnergyResponse(state.energy)) >> IO.pure(EventAction.Ignore)

  end messageHandler

  // --- EVENT HANDLER ---------------------------------------------------------

  // The event handler applies all events to the state and returns the new state.

  private def eventHandler(state: State, event: Event): State = 
    event match
      case AddEnergy(energy) => State(state.energy + energy)
      case UseEnergy(energy) => State(state.energy - energy)

  // The factory method for our actor. It requres the existence of both
  // - An ActorSytem
  // - An EventStore
  def spawn()(using actorSystem: ActorSystem)(using EventStore) = 
    actorSystem.spawnEventSourcedActor[State, Message, Event](
      persistenceId     = PersistenceId.of("energy-tracker-actor"), 
      initialState      = State(),
      messageHandler    = messageHandler,
      eventHandler      = eventHandler,
      snapshotPredicate = SnapshotPredicate.snapshotEvery(10),  // optional
      retention         = Retention(                            // optional
                            purgeOnSnapshot = true,
                            snapshotsToKeep = 1
                          )
    )

end EnergyTrackerActor

object EventSourcedExample extends IOApp.Simple:
  def run: IO[Unit] = 
    // Create and use a resource bracket for an event store.
    // All Peloton systems (including ActorSystem and EventStore) use the
    // Peloton config. There are multiple ways of specifying the Peloton config. 
    // Under the hood, Peloton uses pureconfig and therefore provides the same ways 
    // of configuring the system. There is also a version of `use` that takes an 
    // explicit Config instance as a parameter. This one just uses the default 
    // configuration (usually in `./src/main/resources/application.conf`).
    EventStore.use: _ ?=> 
      ActorSystem.use: _ ?=>
        for
                    // Spawn the tracker actor and send some messages
          actor  <- EnergyTrackerActor.spawn()

          pizza  <- actor ? EnergyTrackerActor.EatPizza(1)
          juice  <- actor ? EnergyTrackerActor.DrinkJuice(2)
          juice  <- actor ? EnergyTrackerActor.DoWorkout(0.5)
          energy <- actor ? EnergyTrackerActor.GetEnergy
          _      <- IO.println(s"The tracker initially reports $energy energy.")

                    // At this point in time, all message have been processed (because 
                    // we've used the ASK pattern). We can terminate the actor and all
                    // still be sure that all events have been persisted.
          _      <- actor.terminate

                    // Now we spawn the tracker actor again. It will fetch all events 
                    // from the event store and reapply them to the state which will 
                    // result in the very same state as before the shutdown.
          actor  <- EnergyTrackerActor.spawn()
          energy <- actor ? EnergyTrackerActor.GetEnergy
          _      <- IO.println(s"The tracker now reports $energy energy.")

        yield ()

end EventSourcedExample