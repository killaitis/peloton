# Introduction to Peloton

This guide introduces the core concepts and actor types provided by Peloton.


## The Actor System

An actor system is the central hub for its actors. It creates and manages actors and can expose them to other actor systems
over HTTP when remote access is configured.

`ActorSystem.use` creates an actor system with the default configuration and releases it when the supplied effect completes:

```scala
ActorSystem.use: actorSystem ?=> 
  // Keep the application running until it is canceled.
  IO.never
```

The actor system is available as a contextual (`given`) value inside the block. You can use it directly or pass it to an
actor factory:

```scala
ActorSystem.use: _ ?=> 
  for
    helloActor <- HelloActor.spawn()
    // ...
    _          <- helloActor.terminate
  yield ()
```

The actor system's lifetime is scoped to the `use` block. On release, it terminates all registered actors. Termination stops
new messages, fails queued ASK requests, and cancels any message handler that is currently running. If a caller must wait for
work to finish, the handler must send its ASK reply only after that work is complete; see [The Ask Pattern](#the-ask-pattern).

## Actors and Interaction Patterns

Actors encapsulate their state and can be interacted with only by sending messages. Messages are placed in an actor's inbox
and processed sequentially, so a handler does not run concurrently with another handler for the same actor. Different callers
can send messages concurrently.

When an actor is created, the actor system returns an `ActorRef[M]`, where `M` is the actor's message type. The reference
provides a typed interface without exposing the actor's implementation or state. An actor can be local or, when configured,
remote.

Spawn methods optionally accept `inboxCapacity`. When the bounded inbox is full, external sends wait until space is available.
The default inbox is unbounded.

*Note:* Examples are available in the repository's [examples directory](../examples/).

Actors provide two interaction patterns: TELL and ASK.

### The Tell Pattern

With TELL, the caller sends a message without requesting a response. When the `tell` effect completes, the message has been
accepted into the inbox; this does not mean that the actor has processed it. Sequential sends from one caller are processed
in inbox order.

Use `tell()` or its `!` alias:

```scala
ActorSystem.use: _ ?=> 
  for
    helloActor <- HelloActor.spawn()

    _          <- helloActor.tell(HelloActor.Message.Hello("World"))
    _          <- helloActor.tell(HelloActor.Message.Hello("Darkness"))
    _          <- helloActor ! HelloActor.Message.Hello("Kitty")

  yield ()
```

The message type on `ActorRef[M]` is checked at compile time. If an inbox is bounded and full, sending also applies backpressure.
TELL does not report whether the actor's handler later succeeds.

### The Ask Pattern

ASK sends a message and waits asynchronously for a response. The caller's `IO` remains pending; it does not block a thread.
Each supported message/response pair must have a `CanAsk[M, R]` instance. This lets the compiler check the pair and infer
the response type. The `?` operator is an alias for `ask()`.

The handler must explicitly reply by calling `context.reply(response)`. An ASK fails if the handler fails, and times out if
no reply arrives before the timeout (one hour by default). A `CanAsk` instance declares a supported protocol pair; it cannot
guarantee that every handler path actually sends a reply.

```scala
object HelloActor:
  sealed trait Message

  object Message:
    final case class Hello(greeting: String) extends Message
    case object HowAreYou extends Message

  object Response:
    final case class HowAreYouResponse(msg: String)

  // Declare the message/response pair supported by ASK.
  given CanAsk[Message.HowAreYou.type, Response.HowAreYouResponse] = canAsk

// ...

ActorSystem.use: _ ?=> 
  for
    helloActor <- HelloActor.spawn() 

    response   <- helloActor.ask(HelloActor.Message.HowAreYou)
    _          <- IO.println(s"The Hello actor says: ${response.msg}")

    response2  <- helloActor ? HelloActor.Message.HowAreYou
  yield ()
```

The actor processes inbox messages sequentially, so earlier messages in the inbox are handled first. The ASK response is
completed when `context.reply` is called, however, which may happen before the handler finishes. For example, an
event-sourced handler can reply before its event is written. Place the reply after the work the caller needs to observe.

### Tell versus Ask

ASK is convenient when a caller needs a result. For a fully asynchronous protocol, include a reply-to `ActorRef[R]` in the
message and have the receiving actor send a TELL response to it. This avoids keeping the caller's `IO` pending while the
request is handled.

## Stateful Actors

Stateful actors maintain a mutable state of a specific type in memory. They are created using the `spawnActor()`
method of the `ActorSystem`. The method takes an optional name (a random one is generated if omitted),
the initial state and the initial behavior for the actor.

The behavior of a stateful actor has type `Behavior[S, M]`, where `S` is the state type and `M` is the message type.
Its `receive` method takes the current state, the message, and the `ActorContext[S, M]`, then returns an
`IO[Behavior[S, M]]`. A handler can return the current behavior or a different one to change how the actor handles
subsequent messages.

Use `ActorContext.setState()` to replace the actor's state. The context also provides `scheduleOnce()` for delayed
self-messages; outstanding timers are canceled when the actor terminates.

Spawn methods accept an optional `inboxCapacity`. When set, external TELL and ASK submissions apply backpressure while
the inbox is full. The message currently being processed, self-messages, and stashed messages do not count toward the
limit. The default is an unbounded inbox.

`ActorContext.currentBehavior` and `currentBehaviorM` provide the current behavior directly or wrapped in `IO`.
Context operations such as `setState`, `reply`, and `stash` already return the current behavior, so an explicit return
is often unnecessary.


```scala

object MyActor:

  sealed trait MyMessage
  final case class SetInt(i: Int) extends MyMessage
  final case class SetString(s: String) extends MyMessage

  case class MyState(i: Int, s: String)

  private val behavior: Behavior[MyState, MyMessage] =
    (state, message, context) => message match
      case SetInt(newI) =>
        context.setState(state.copy(i = newI))

      case SetString(newS) =>
        context.setState(state.copy(s = newS))

  def spawn()(using actorSystem: ActorSystem) =
    actorSystem.spawnActor[MyState, MyMessage](
      name            = Some("MyActor"),
      initialState    = MyState(i = 3, s = "x"),
      initialBehavior = behavior
    )
end MyActor

```

This example shows a stateful actor that supports the ASK pattern:

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
      case Message.Hello(_) =>
        context.setState(State(alreadyGreeted = true))

      case Message.HowAreYou =>
        if state.alreadyGreeted
        then context.reply(Response.HowAreYouResponse("I'm fine!"))
        else context.reply(Response.HowAreYouResponse("I'm not fine yet."))

  def spawn()(using actorSystem: ActorSystem) =
    actorSystem.spawnActor[State, Message](
      name            = Some("HelloActor"),
      initialState    = State(alreadyGreeted = false),
      initialBehavior = behavior
    )

end HelloActor
```

*Note:* Every handler path for an ASK-enabled message must call `context.reply` with the declared response type.
If it does not reply, the caller waits until the ASK timeout.

### Stashing messages

`context.stash()` moves the message currently being processed to the actor's stash. The actor can later call
`context.unstashAll()` to move stashed messages back to the inbox. They are placed ahead of messages that were already
waiting in the inbox, so they are processed first.

## Durable State Actors

Unlike stateful actors, which keep their state only in memory, durable state actors persist each state update to an
external store. When an actor starts, it loads the latest state for its persistence ID. If no state exists yet, it
uses the supplied initial state.

Create one with `ActorSystem.spawnDurableStateActor()`. In addition to the initial state and behavior, it requires a
`PersistenceId`, which identifies the actor's record in the store. Peloton does not check that persistence IDs are
unique, so the application must ensure that each actor instance uses the correct ID.

Use `ActorContext.setState()` to update a durable state actor. The update is written to the store before the in-memory
state is advanced. A durable state actor requires given instances of:

* `PayloadCodec[S]`, to encode and decode the actor's state.
* `DurableStateStore`, which reads and writes the encoded state.

Peloton provides two payload codecs:

* `JsonPayloadCodec` stores JSON-encoded bytes, which are easier to inspect.
* `KryoPayloadCodec` stores compact binary data, which is not human-readable.

Choose a codec based on your compatibility and operational needs; do not assume that serialized data remains compatible
across arbitrary changes to your state types or codec.

`DurableStateStore.use()` provides a configured store for a resource scope. The configuration is read from the default
Typesafe Config sources; see [Peloton Configuration](./configuration.md) for backend setup.

Persistence drivers are separate modules and are not included in `peloton-core`:

```sbt
libraryDependencies += "de.killaitis" %% "peloton-persistence-postgresql" % PelotonVersion
libraryDependencies += "de.killaitis" %% "peloton-persistence-mysql" % PelotonVersion
libraryDependencies += "de.killaitis" %% "peloton-persistence-cassandra" % PelotonVersion
```

MySQL and Cassandra support is experimental. See the [durable state example](../examples/src/main/scala/DurableStateExample.scala).

## Event-Sourced Actors

Event-sourced actors persist events rather than each new state. Their state is kept in memory while running and is rebuilt
when the actor starts by loading the latest snapshot, if one exists, and replaying the events that follow it. The
supplied initial state is used when there is no stored snapshot or event.

Create one with `ActorSystem.spawnEventSourcedActor[S, M, E]`. It requires:

* `PersistenceId`, a unique identifier for this actor's event stream.
* `MessageHandler[S, M, E]`, which handles a message and returns `EventAction.Ignore` or `EventAction.Persist(event)`.
  It may also reply to an ASK through the actor context.
* `EventHandler[S, E]`, which applies an event to the current state and returns the new state.
* An `EventStore`, a `PayloadCodec[E]` for events, and a `PayloadCodec[S]` for snapshots.

When the message handler returns `Persist`, Peloton writes the event before applying it to the in-memory state. The event
handler is also used to replay stored events at startup. Event-sourced actors keep a fixed behavior; their state changes
through events rather than through `ActorContext.setState()`.

Snapshots are optional and disabled by default. A `SnapshotPredicate` decides when to write one. `Retention` can purge
events and older snapshots after a snapshot is written; configure it carefully because purged events are no longer
available for replay. See the [event-sourced example](../examples/src/main/scala/EventSourcedExample.scala).

## Remote Actors

To expose an actor system over HTTP, configure `peloton.http` with a hostname and port. The actor system starts the
HTTP server when it is created:

```hocon
peloton {
  http {
    # Use an address reachable by clients; 127.0.0.1 is local to this machine.
    hostname = "127.0.0.1"
    port     = 5000
  }
}
```

Give remotely addressable actors an explicit name when spawning them. A client can then create a remote reference using
a URI such as `peloton://127.0.0.1:5000/my-actor`. Remote references support TELL and ASK; terminating a remote actor
through its reference is not supported.

Remote messages and responses are serialized with Kryo and transported over HTTP. Both sides therefore need compatible
message and response types. The `peloton://` URI scheme identifies a Peloton reference; the transport itself is plain
HTTP.

**Security:** The remote endpoint has no built-in authentication or TLS. It also deserializes payloads with Kryo. Do not
expose it to untrusted networks; bind it to a trusted interface or protect it with a suitably configured network boundary
and TLS/authenticating proxy. See [Peloton Configuration](./configuration.md) for the HTTP settings.

## Scheduled Effects

The separate `peloton-scheduling-cron` module schedules Cats Effect `IO` values with Quartz-compatible CRON expressions.
Add the module as a dependency:

```sbt
libraryDependencies += "de.killaitis" %% "peloton-scheduling-cron" % PelotonVersion
```

Create a `CronScheduler` resource with `CronScheduler.use`, then use the extensions from `CronScheduler.syntax`:

```scala
import cats.effect.IO
import peloton.scheduling.cron.CronScheduler
import peloton.scheduling.cron.CronScheduler.syntax.*
import scala.concurrent.duration.*

CronScheduler.use: _ ?=>
  for
    task <- IO.println("Scheduled task")
              .scheduledTask(
                cron = "*/5 * * ? * *",
                onError = error => IO.println(s"Scheduled task failed: $error")
              )
    _    <- IO.sleep(30.seconds)
    _    <- task.cancel
  yield ()
```

`scheduledTask` returns a handle that can cancel the task or observe its consumer fiber with `join`. The convenience
extension `scheduled` returns no handle. Releasing the scheduler cancels all tasks. By default, effect failures are
ignored; provide `onError` to handle them. See the [scheduled effects example](../examples/src/main/scala/ScheduledEffects.scala).

Actors can also schedule delayed self-messages with `ActorContext.scheduleOnce`; these timers are canceled when the actor
terminates. This is separate from CRON scheduling.

