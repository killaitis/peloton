# Introduction to Peloton

This document will introduce you to the core concepts used by the Peloton library.


## The Actor System

An actor system is the central hub for actors. It is used to create new actors, manage their lifetime and possibly transport 
messages to other actor systems on remote machines.

You create a new actor system with default settings:

```scala
ActorSystem.use: actorSystem ?=> 
  // use the actor system
  IO.never // use it forever
```

Usually, to create an actor you would pass the actor system as a given instance to the factory method of the actor class. 
The provided actor system is already a `given` instance, so if you do not need direct access to the actor system, but only 
a given instance, you can simply write (`use { _ ?=> ... }`). 

```scala
ActorSystem.use: _ ?=> 
  for
    helloActor <- HelloActor.spawn()
    // ...
    _          <- helloActor.terminate
  yield ()
```

The lifetime of the provided actor system is scoped to the `use {}` block. All actors that are still running at the end of the block 
will be automatically terminated before the actor system is shut down, so manually terminating an actor is optional, unless your 
actor logic requires it to be terminated.

## Actors and Interaction Patterns

Actors are fenced objects that can only be interacted with by sending them messages. Messages sent to an actor are enqueued in the actor's 
message queue and the actor will process these messages one by one, guaranteeing the actor's message handler a concurrency-free access 
to the internal state of the actor when processing the messages (though you can concurrently send messages to the actor). 

You never get direct access to the data structures that back an actor. Instead, on creation, the actor system will return a data structure
called `ActorRef[M]`, where `M` is the type of messages this actor will support to receive to ensure type safety. An `ActorRef` is like 
a kind of pointer to the real actor and provides an interface to communicate with it. This is to hide the many possibilities how an actor 
might actually be implemented, or even where it is located (remote actors can be even on a totally different machine).

Actors provide two different interaction patterns to the client: the TELL and the ASK pattern.

*Note:* The code examples below are taken from the examples provided in this repository at the [examples](./examples/) folder.

### The Tell Pattern

Using the TELL pattern means that the message is just sent to the actor's message inbox and no response is expected. The control flow 
is yielded back to the client immediately after the message is put into the actor's message inbox. At this time, the actor has probably 
not yet processed the message as the message processing is performed asynchronously, so a certain actor state can not be assumed. 
On the other hand, it is guaranteed that the order of the messages in the actor's message inbox strictly follows the order of calls 
in the client code (if done synchronously).

In Peloton, to use the TELL pattern you call the `tell()` method of a given actor reference. You can also use the operator `!` instead, 
which is just an alias for `tell()`. 

```scala
ActorSystem.use: _ ?=> 
  for
    // Create a new actor. This will return an ActorRef[Message], which means that this actor 
    // will only support messages of type Message.
    helloActor <- HelloActor.spawn() 

    // Send messages with tell() method
    _          <- helloActor.tell(HelloActor.Message.Hello("World"))
    _          <- helloActor.tell(HelloActor.Message.Hello("Darkness"))

    _          <- helloActor.tell("Hello") // This will NOT compile!
    _          <- helloActor.tell(42)      // This will NOT compile!

    // Send messages with the ! alias
    _          <- helloActor ! HelloActor.Message.Hello("Kitty")

    // At this time, the messages are guaranteed to have arrived in the actor message inbox, 
    // but they are probably not processed yet. They may have, but there is no guarantee.

    // ...
  yield ()
```

Actors, their message inboxes and their message handlers are bound to the scope of the actor system they are created in. Terminating 
the actor system will also terminate all of its actor's message handlers, even when currently processing a message, so be careful when 
to terminate the actor system.

### The Ask Pattern

While in the TELL pattern sending messages is completely isolated from processing them, it's the opposite in the ASK pattern. To use 
it, you call `ask()` on the actor reference, like you call `tell()` in the TELL pattern. The difference is that the call to `ask()`

* is blocked until the message (and all other preceeding messages in the message inbox as well!) is fully 
processed by the actor's message mandler
* will return a value of a specific type, while `tell()` will just return `Unit`.

Unlike the TELL pattern which every actor supports out of the box, the ASK pattern has to be explicitly made available by the actor 
for a specific combination of message and response type. Thus, you cannot simply call `ask()` for any message type the actor supports.

To enable a specific combination of message and response type to be ASK-able, there has to be a given instance of `CanAsk[M, R]`, 
where `M` is the message type and `R` the response type. You can create such an instance by using the `Actor.canAsk` function.
This gives a compile-time type safety for each call to `ask()`, as it is the Scala compiler that checks for the existence of a
`CanAsk[M, R]` for the provided message type `M` and can then infer the expected response type `R`.

*Note:* You can use the operator `?` as an alias for `ask()`.

which is just an alias for `tell()`. 
*Note:* This is in contrast to Akka Typed which uses a generic response type `R` for all responses but cannot differentiate between 
combinations of subtypes of `M` and `R` which usually makes it necessary to apply pattern matching after each `ask()` in case the 
response type is polymorphic.

*Note:* The actor's message handler has to actively return a value of the response type as well. This will be covered later in the 
sections about different actor types.

```scala
object HelloActor:
  sealed trait Message

  object Message:
    final case class Hello(greeting: String) extends Message
    case object HowAreYou extends Message

  object Response:
    final case class HowAreYouResponse(msg: String)

  // This wires a specific message type to a specific response type.
  given CanAsk[Message.HowAreYou.type, Response.HowAreYouResponse] = canAsk

// ...

ActorSystem.use: _ ?=> 
  for
    helloActor <- HelloActor.spawn() 

    // ASK the actor. Due to the existence of a specific CanAsk for our message type 
    // HelloActor.Message.HowAreYou, the compiler can infer the result type
    // HelloActor.Response.HowAreYouResponse.
    response   <- helloActor.ask(HelloActor.Message.HowAreYou)
    _          <- IO.println(s"The Hello actor says: ${response.msg}")

    // ASK with operator
    response2  <- helloActor ? HelloActor.Message.HowAreYou

    // At this time, the message is guaranteed to be processed by the actor.
  yield ()
```

### Tell versus Ask

The ASK pattern provides an easy way for a direct (pseudo-synchronous) communication with an actor. 
But it comes with a price: the client code is logically blocking during the call to `ask()`. To avoid this,
you can use a protocol that adds the caller's address (probably an `ActorRef[R]`) to the message, then TELL 
the message to the actor and the actor can then TELL back the response to the client, making both actors 
decoupled.

*Note:* Be aware that providing ASKs to an actor's protocol always adds complexity to the actor as it has to create 
and handle resources that it wouldn't need with TELL, making it basically slower. So, if there is no need to
provide ASK in your actor protocol, don't add it!

## Stateful Actors

Stateful actors maintain a mutable state of a specific type in memory. They are created using the `spawnActor()`
method of the `ActorSystem`. The method takes an optional name (a random one is generated if omitted),
the initial state and the initial behavior for the actor.

The message handler of a stateful actor is of type `Behavior[S, M]`, where `S` is the type of the actor's state 
and `M` is the type of the actor's messages. The type `Behavior[S, M]` is a single-method trait with an 
abstract method called `receive()`, which provides the current state, the message and the current actor context 
as parameters and returns an `IO[Behavior[S, M]]`, i.e., a (possibly) new behavior. This allows an actor to 
react on the current state and the message to change its behavior. 

The actor context can be used to modify the current state of the actor using `Context.setState()`.

To return the current behavior, the context provides the methods `Context.currentBehavior()` (non-effectful)
and `Context.currentBehaviorM()` (wrapped in a pure effect). Most methods of `Context` already return the 
current behavior, so this can often be omitted. 


```scala

object MyActor:

  sealed trait MyMessage
  final case class SetInt(i: Int) extends MyMessage
  final case class SetString(s: String) extends MyMessage

  case class MyState(i: Int, s: String)

  private val behavior: Behavior[MyState, MyMessage] =
    (state, message, context) => message match
      case SetInt(newI) => 
        context.setState(state.copy(i = newI)) >> 
        context.currentBehaviorM 
        
      case SetString(newS) => 
        context.setState(state.copy(s = newS))  // setState automatically returns 
                                                // the current behavior, so currentBehaviorM 
                                                // can be omitted.

  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[MyState, MyMessage](
      name            = Some("MyActor"), // An optional name for the actor
      initialState    = MyState(i = 3, s = "x"),
      initialBehavior = behavior
    )
end MyActor

```

Next, we'll define an actor that supports the ASK pattern:

```scala
object HelloActor:

  case class State(alreadyGreeted: Boolean)

  sealed trait Message

  object Message:
    final case class Hello(greeting: String) extends Message
    case object HowAreYou extends Message

  object Response:
    final case class HowAreYouResponse(msg: String)

  // This enables the ASK pattern for the given message and response types.
  given CanAsk[Message.HowAreYou.type, Response.HowAreYouResponse] = canAsk

  private val behavior: Behavior[State, Message] = 
    (state, message, context) => message match
      case Message.Hello(greeting) => // Hello is TELL-only => no response
        context.setState(alreadyGreeted = true)
        
      case Message.HowAreYou => // HowAreYou is ASK-able => send response
        if state.alreadyGreeted
        then context.reply(Response.HowAreYouResponse("I'm fine!")) 
        else context.reply(Response.HowAreYouResponse("Nobody likes me.")) 

  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[State, Message](
      name            = Some("HelloActor"),
      initialState    = State(alreadyGreeted = false),
      initialBehavior = behavior
    )
    
end HelloActor
```

*Note:* be careful when activating ASK for an actor! Make sure that the behavior will *always* return 
a valid value of the promised type. Otherwise the client will be stuck forever,

## Durable State Actors

Unlike stateful actors which hold their current state in memory only, durable state actors use an 
external storage system to make their state persistent. Durable state actors start with a given initial 
state when they are created the first time, i.e., the actor has never before written its state, 
and read the state from the external storage system on each consecutive start.

Durable state actors are spawned using the method `spawnDurableStateActor()` of the `ActorSystem`, 
which - like stateful actors - takes an initial state and an initial behavior as arguments, but 
additionally takes a persistence ID argument. The persistence ID is the primary key that is used
by the underlying storage system for this actor, so it has to be unique. There is no check for
uniqueness, so be careful when assigning the persistence ID. 

To change the state of the durable state actor, the actor contect provides the `setState()` method - like the stateful actor. 
So in usage of the state, durable state actors and stateful actors do not differ. The difference 
bethween both is that, for the durable state actor, you have to provide given instances of 

* `PayloadCodec[S]`: used to encode the current state of the actor (when updating its state 
and thus writing it to the storage) and decode the previous state when respawning the actor. Notice that
the payload codec is typed with the actor's state type.
* `DurableStateStore`: used to read and write instances of the actor's state type. The payload is 
stored in a binary representation, so it uses a given payload codec to encode and decode the payload.

Currently, Peloton supports two different payload codecs:
* `JsonPayloadCodec`: uses Json format. Generates a larger, but human-readable binary representation. 
Good for development or if performance is not the most important factor.
* `KryoPayloadCodec`: Uses the much more compact Kryo format. Generates a much smaller binary 
representation that is not human-readable. Typically used for high-volume production environments.

An instance of `DurableStateStore` can be created using `DurableStateStore.make()`. There are two 
versions: one that takes an explicit storage configuration as a parameter and one without parameters. 
The first one allows you to explicitly define your storage backend programmatically while the 
latter will read the default configuration from the `application.conf` (or all other ways that 
Pureconfig provides).

Currently, Peloton comes with support for 
* `postgresql`
* `mysql` (experimental, needs more testing)
* `cassandra` (experimental, needs more testing)

These drivers are not incuded in the core Peloton package. You have to add them to your dependencies. 

```sbt
libraryDependencies += "de.killaitis" %% "peloton-persistence-postgresql" % PelotonVersion
libraryDependencies += "de.killaitis" %% "peloton-persistence-mysql" % PelotonVersion     // experimental
libraryDependencies += "de.killaitis" %% "peloton-persistence-cassandra" % PelotonVersion // experimental
```

`DurableStateStore.make()` returns a `Resource` value. If you just want to use it as a given value,
you can just use `DurableStateStore.use()`.

You can find a simple example [here](../examples/src/main/scala/DurableStateExample.scala) 

## Event-Sourced Actors

## Remote Actors

## Scheduled Effects

