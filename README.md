
[![Continuous Integration](https://github.com/killaitis/peloton/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/killaitis/peloton/actions/workflows/ci.yml) 
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![MIT License](https://img.shields.io/github/license/killaitis/peloton.svg?maxAge=3600)](http://www.opensource.org/licenses/mit-license.php)
[![Release Notes](https://img.shields.io/github/release/killaitis/peloton.svg?maxAge=3600)](https://github.com/killaitis/peloton/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/de.killaitis/peloton-core_3)](https://search.maven.org/artifact/de.killaitis/peloton-core_3) 

<div>
<a href="https://www.scala-lang.org/"><img src="https://img.shields.io/badge/scala-%23DC322F.svg?style=for-the-badge&logo=scala&logoColor=white" /></a>
<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>
<a href="https://typelevel.org/cats-effect/"><img src="https://typelevel.org/cats-effect/img/cats-effect-logo.svg" height="40px" align="right" alt="Cats Effect" /></a>
<a href="https://fs2.io/"><img src="https://fs2.io/_media/logo_small.png" height="40px" align="right" alt="Functional Streams for Scala" /></a>
</div>


# Peloton – Actors for the Typelevel ecosystem 

<p></p>
<div align="center">
  <img src="./img/kitten.png" alt="Playful Kitten"/>
</div>

# 

Peloton aims to be a lightweight and playful actor library for Cats Effect. It provides support for

- **Actor systems**: control the lifespan of actors and ensure safe resource management.
- **Stateful actors**: actors that hold and modify a state of a specific type in memory.
- **Event sourced actors**: actors that convert messages to events that are persisted in an event store and replayed adter the actor restarts.
- **Durable state actors**: actors that hold a state of a specific type in memory and can store the state in a durable state store. This state is then restored 
  automatically after the actor restarts.
- **Message stashes**: each actor is able to postpone the processing of incoming messages by pushing them to a stash and can pull back the messages from the stash later
- **Changing behavior**: the actor's message handler, i.e., the current behavior, possibly returns a new behavior - depending on the actor's state and/or the message.
- **Remote actors**: an actor system can provide an HTTP interface to other actor system on different hosts and make its local actors available to these actor systems.
- **Scheduled effects**: effects can be scheduled using a Quartz-compatible CRON expression and triggered and evaluated in the background.

Peloton actors are designed to work together with your Cats Effect application. All actor operations and interactions are effectful in the `IO` effect type.

## Get started

The Peloton library is split into separate packages. This allows you to just include what you really need.

For the core Peloton functionality (which is, in fact, all but database-specific persistence drivers), add the following dependency to your `build.sbt` file:

```sbt
// See the latest release version on the badge above
val PelotonVersion = "<version>"

// Peloton base functionality
libraryDependencies += "de.killaitis" %% "peloton-core" % PelotonVersion
```

In order to use persistent actors (durable state or event sourced), you have to add a Peloton persistence module to your `build.sbt` file that supports your database:

```sbt
// PostgreSQL driver for Peloton persistent actors
libraryDependencies += "de.killaitis" %% "peloton-persistence-postgresql" % PelotonVersion
```

There is also a module that allows for scheduling Cats Effect IO actions in the background using the Quartz CRON scheduler. To use it, add the following dependency to your `build.sbt` file:

```sbt
// CRON-based background scheduling of Cats Effect actions.
libraryDependencies += "de.killaitis" %% "peloton-scheduling-cron" % PelotonVersion
```

Peloton is only available for Scala 3. Support for older releases of Scala is currently not planned.


## Introduction

To give an overview of the architecture and terminology of Peloton, refer to the [introduction to Peloton](./docs/introduction.md).


## Examples

For a quick start, examples can be found in the [examples](./examples/) folder.

You can also find additional information about Peloton actors in the [Peloton tests](./core/src/test/scala/peloton/actors/)


## Configuration

An Peloton configuration description and reference can be found [here](./docs/configuration.md).
