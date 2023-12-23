
[![Continuous Integration](https://github.com/killaitis/peloton/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/killaitis/peloton/actions/workflows/ci.yml) 
[![Maven Central](https://img.shields.io/maven-central/v/de.killaitis/peloton-core_3)](https://search.maven.org/artifact/de.killaitis/peloton-core_3) 
[![Release Notes](https://img.shields.io/github/release/killaitis/peloton.svg?maxAge=3600)](https://github.com/killaitis/peloton/releases/latest)
[![MIT License](https://img.shields.io/github/license/killaitis/peloton.svg?maxAge=3600)](http://www.opensource.org/licenses/mit-license.php)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

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

- *stateful actors*: actors that hold and modify a state of a specific type in memory.
- *event sourced actors*: actors that convert messages to events that are persisted in an event store and replayed adter the actor restarts.
- *durable state actors*: actors that hold a state of a specific type in memory and can store the state in a durable state store. This state is then restored 
  automatically after the actor restarts.
- *message stashes*: each actor is able to postpone the processing of incoming messages by pushing them to a stash and can pull back the messages from the stash later
- *changing behavior*: the actor's message handler, i.e., the current behavior, possibly returns a new behavior - depending on the actor's state and/or the message.
- *scheduled effects*: effects can be scheduled using a Quartz-compatible CRON expression and triggered and evaluated in the background.

Peloton actors are designed to work together with your Cats Effect application. All actor operations and interactions are effectful in the `IO` effect type.

## Get started
Add the following dependencies to your `build.sbt` file:
```sbt
libraryDependencies ++= Seq(
  "de.killaitis" %% "peloton-core"                    % PelotonVersion,
  "de.killaitis" %% "peloton-cron"                    % PelotonVersion, // optional
  "de.killaitis" %% "peloton-persistence-postgresql"  % PelotonVersion  // optional
)
```

Peloton is available for Scala 3. Support for older releases of Scala is currently not planned.

## Concepts
The core concepts of Peloton are described [here](./docs/Concepts.md).

## Examples
Examples can be found in the `examples` folder.
