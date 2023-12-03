# Peloton – Actors for Cats Effect 

<style>
.column {
  float: left;
  width: 50%;
}

.row:after {
  content: "";
  display: table;
  clear: both;
}

.badges {
  margin-top: 10px;
  margin-bottom: 20px;
}
</style>

<div class="row">
<div class="column"><img src="./img/kitten.png" alt="Playful Kitten"/></div>
<div class="column" align="right">
  <a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" alt="Cats friendly" /></a>
  <a href="https://typelevel.org/cats-effect/"><img src="https://typelevel.org/cats-effect/img/cats-effect-logo.svg" height="40px" alt="Cats Effect" /></a>
  <a href="https://fs2.io/"><img src="https://fs2.io/_media/logo_small.png" height="40px" alt="Functional Streams for Scala" /></a>
</div>
</div>

<div class="badges"><img src="https://img.shields.io/badge/scala-%23DC322F.svg?style=for-the-badge&logo=scala&logoColor=white" /></div>
  

Peloton aims to be a lightweight and playful actor library for Cats Effect. It provides support for

- stateful actors: actors that hold and modify a state of a generic type in memory.
- persistent actors: actors that hold a state of a generic type in memory and can store the state in a durable state store. This state is then restored 
  automatically after the application restarts.

Peloton actors are designed to work together with your Cats Effect application. All actor operations and interactions are effectful in the `IO` effect type.


## Get started
Add the following dependency to your `build.sbt` file:
```sbt
libraryDependencies += "de.killaitis" %% "peloton-core" % "0.0.1"
```

## Concepts
TODO

### The Actor System

### Actors and Interaction Patterns

#### The Tell Pattern

#### The Ask Pattern

### Stateful Actors

### Persistent Actors

## Examples

Examples can be found in `core/src/examples`.
