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

Actors are closed objects that can only be accessed by sending them messages. Messages sent to an actor are enqueued in the actor's 
message queue and the actor will process these messages one by one, guaranteeing a concurrency-free access to the internal state of 
the actor when processing the messages (though you can concurrently send messages to the actor). 

Actors provide two different interaction patterns: the tell and the ask pattern.

### The Tell Pattern

### The Ask Pattern

## Stateful Actors

## Persistent Actors

## Remote Actors
