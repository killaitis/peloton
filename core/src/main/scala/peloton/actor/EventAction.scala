package peloton.actor

import cats.effect.IO

enum EventAction[+E]:
  case Ignore
  case Store(event: E)
  case Snapshot

type MessageHandler[S, M, E] = (state: S, message: M, context: ActorContext[S, M]) => IO[EventAction[E]]
type EventHandler[S, E] = (state: S, event: E) => S
