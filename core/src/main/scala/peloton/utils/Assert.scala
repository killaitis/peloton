package peloton.utils

import cats.effect.IO

extension (o: IO.type)
  def assert(predicate: => Boolean)(assertionError: Throwable): IO[Unit] = 
    if predicate then IO.unit else IO.raiseError(assertionError)
