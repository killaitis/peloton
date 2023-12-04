package peloton.utils

import cats.effect.IO

extension (o: IO.type)
  def assert(predicate: => Boolean)(assertionError: String): IO[Unit] = 
    if predicate then IO.unit else IO.raiseError(new AssertionError(assertionError))
