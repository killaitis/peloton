# Concepts
This document will describe the core concepts used by the Peloton library.

## The Actor System

Create a new actor system with default settings:
```scala
ActorSystem.withActorSystem { actorSystem => 
}
```

Create a new implicit/given actor system with default settings:
```scala
ActorSystem.withActorSystem { case given ActorSystem => 
}
```

## Actors and Interaction Patterns

### The Tell Pattern

### The Ask Pattern

## Stateful Actors

## Persistent Actors

## Remote Actors
