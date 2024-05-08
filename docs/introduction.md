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

## Persistent Actors

## Remote Actors

## Scheduled Effects

