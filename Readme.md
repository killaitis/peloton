# Peloton – Actors for Cats Effect 

[![Continuous Integration](https://github.com/killaitis/peloton/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/killaitis/peloton/actions/workflows/ci.yml) 
[![Maven Central](https://img.shields.io/maven-central/v/de.killaitis/peloton-core_3)](https://img.shields.io/maven-central/v/de.killaitis/peloton-core_3) 
<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>
<a href="https://typelevel.org/cats-effect/"><img src="https://typelevel.org/cats-effect/img/cats-effect-logo.svg" height="40px" align="right" alt="Cats Effect" /></a>
<a href="https://fs2.io/"><img src="https://fs2.io/_media/logo_small.png" height="40px" align="right" alt="Functional Streams for Scala" /></a>

<img src="https://img.shields.io/badge/scala-%23DC322F.svg?style=for-the-badge&logo=scala&logoColor=white" />

<p>
<div align="center"><img src="./img/kitten.png" alt="Playful Kitten"/></div>
</p>

Peloton aims to be a lightweight and playful actor library for Cats Effect. It provides support for

- stateful actors: actors that hold and modify a state of a generic type in memory.
- persistent actors: actors that hold a state of a generic type in memory and can store the state in a durable state store. This state is then restored 
  automatically after the application restarts.
- message stashes: each actor is able to postpone the processing of incoming messages by pushing them to a stash and can pull back the messages from the stash later
- changing behavior: the actor's message handler, i.e., the current behavior, possibly returns a new behavior - depending on the actor's state and/or the message.

Peloton actors are designed to work together with your Cats Effect application. All actor operations and interactions are effectful in the `IO` effect type.

## Get started
Add the following dependency to your `build.sbt` file:
```sbt
libraryDependencies += "de.killaitis" %% "peloton-core" % "0.1.0"
```

Peloton is available for Scala 3. Support for older releases of Scala is currently not planned.

## Concepts
The core concepts of Peloton are described [here](./docs/Concepts.md).

## Examples
Examples can be found in the `examples` folder.
