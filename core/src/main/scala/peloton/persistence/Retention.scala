package peloton.persistence

/**
  * Retention parameters. 
  * 
  * Determines the purging behavior of events and snapshots after creating 
  * a new snapshot.
  *
  * @param purgeOnSnapshot
  * @param snapshotsToKeep
  */
final case class Retention(
  purgeOnSnapshot: Boolean = false,
  snapshotsToKeep: Int = 0
)
