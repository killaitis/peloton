package peloton.persistence

type SnapshotPredicate[S, E] = (state: S, event: E, numEvents: Int) => Boolean

object SnapshotPredicate:
  def noSnapshots[S, E]: SnapshotPredicate[S, E] = (_, _, _) => false
  def snapshotEvery[S, E](n: Int): SnapshotPredicate[S, E] = (_, _, count) => count % n == 0
