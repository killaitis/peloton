# Changelog

## v0.4.0
- Added support for event and snapshot retention for event sourced actors. 
- Added `KryoPayloadCodec` as a faster alternative to the standard `JsonPayloadCodec` for the durable state and event store.
- *API CHANGE*: The various versions of `ActorSystem.spawn` have been split to allow for default parameters. The new methods
  also are more precise in their name about what they are actually doing:
  + `spawnActor`
  + `spawnDurableStateActor`
  + `spawnEventSourcedActor`

## v0.3.0
- Introduced event sourced actors.
- Introduced CRON-based scheduling for Cats Effect.
- Improved store factory for better handling of `DurableStateStore` and `EventStore`.
- *API CHANGE*: `ActorContext.respond` has been renamed to `ActorContext.reply`.

## v0.2.1
- Dependency updates 
- Switched releases to new artifact versioning scheme. Artifacts now omit the `v` in the version string (e.g. `peloton-core_3-0.2.1`).

## v0.2.0
- Added support for remote actors. 
- Improved handling of scoped actor systems

## v0.1.0
- Initial release. Basic support for stateful and persistent actors