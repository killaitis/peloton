package peloton.persistence


/**
  * Retention parameters. 
  * 
  * Determines the purging behavior of events and snapshots after creating 
  * a new snapshot.
  *
  * @param purgeOnSnapshot
  *   Flag. If set to `true`, the event store will keep a maximum of `snapshotsToKeep` snapshots that 
  *   exist in the event store (including the one just created) and purge all events and snapshots that 
  *   are older. If set to `false`, no purging will be performed.
  * @param snapshotsToKeep
  *   Number of snapshots to keep when purging is requested (`purgeOnSnapshot` is set to `true`). 
  * 
  *   **Attention!** This number includes the snapshot just created, so if this parameter is set to 0, 
  *   all snapshots (including the the one just created!) will be deleted.
  */
final case class Retention(
  purgeOnSnapshot: Boolean = false,
  snapshotsToKeep: Int = 0
)
