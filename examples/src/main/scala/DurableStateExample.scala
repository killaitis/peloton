import peloton.actor.Actor.{CanAsk, canAsk}
import peloton.actor.{ActorSystem, Behavior}
import peloton.persistence.{
  PersistenceId, 
  PayloadCodec, 
  KryoPayloadCodec, 
  DurableStateStore
}

import cats.effect.{IO, IOApp}


object DurableStateCollectorActor:

  // The state of the actor
  final case class State(words: List[String] = Nil)

  // The protocol
  sealed trait Message
  object Message:
    // Add: adds a word to the word list and return the new word as an AddResponse
    final case class Add(word: String) extends Message

    // Get: returns the current word list as a GetResponse
    final case class Get() extends Message

  object Response:
    final case class AddResponse(wordAdded: String)
    final case class GetResponse(words: List[String])

  // Define the ASK patterns.
  given CanAsk[Message.Add, Response.AddResponse] = canAsk
  given CanAsk[Message.Get, Response.GetResponse] = canAsk

  // The behavior. Durable state actors use the actor context to modify the state.
  // In fact, `ActorContext.setState` will update both the in-memory state and the 
  // version in the persistent durable state store as well. 
  private val behavior: Behavior[State, Message] =
    (state, message, context) => message match
      case Message.Add(word) => 
        context.setState(State(state.words :+ word)) >> 
        context.reply(Response.AddResponse(word))

      case Message.Get() => 
        context.reply(Response.GetResponse(state.words))

  // A durable state actor requires the existence a given instance of a PayloadCodec 
  // for its state type in scope. A KryoPayloadCodec usually fits.
  private given PayloadCodec[State] = KryoPayloadCodec.create

  // The factory method for our actor. It requres the existence of both
  // - An ActorSytem
  // - A DurableStateStore
  def spawn()(using actorSystem: ActorSystem)(using DurableStateStore) = 
    // The actor system provided various methods to spawn the different types 
    // of actors, including durable state actors. The persistenceId parameter is
    // what differentiates the simple just-in-memory actor from the durable state 
    // actor. It is the key which is used to store and read the state to/from the
    // persistent durable state store. 
    // 
    // Note:
    // - A PersistenceId can be constructed from any non-empty String. 
    // - The system does not check if the PersistenceId of your actor is used
    //   multiple times. You have to ensure this on your own.
    actorSystem.spawnDurableStateActor[State, Message](
      persistenceId   = PersistenceId.of("word-collector-actor"),
      initialState    = State(),
      initialBehavior = behavior
    )

end DurableStateCollectorActor

object DurableStateExample extends IOApp.Simple:

  def run: IO[Unit] =
    // Create and use a resource bracket for a durable state store.
    // All Peloton systems (including ActorSystem and DurableStateStore) use the
    // Peloton config. There are multiple ways of specifying the Peloton config. 
    // Under the hood, Peloton uses pureconfig and therefore provides the same ways 
    // of configuring the system. There is also a version of `use` that takes an 
    // explicit Config instance as a parameter. This one just uses the default 
    // configuration (usually in `./src/main/resources/application.conf`).
    DurableStateStore.use: _ ?=> 
      ActorSystem.use: _ ?=>
        import DurableStateCollectorActor.Message.*

        for 
                    // Spawn the new actor. If it has already been spawned before, it will 
                    // fetch the latest revision of the state from the durable state store
          actor  <- DurableStateCollectorActor.spawn()

                    // TELL the actor to add some words
          _      <- actor ! Add("actors")
          _      <- actor ! Add("are")
          _      <- actor ! Add("great")

                    // ASK the actor about the current word list. Due to the nature of the 
                    // ASK pattern, it is guaranteed that all previous message have also been 
                    // processed, i.e., we can assume that the new state of the actor is 
                    // persisted in the durable state store.
          words  <- actor ? Get()
          _      <- IO.println(s"collected words: $words")
        yield ()

end DurableStateExample