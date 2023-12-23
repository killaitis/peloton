# Changelog

## v0.3.0
- Introduced event sourced actors.
- Introduced CRON-based scheduling for Cats Effect.
- Improved store factory for better handling of `DurableStateStore` and `EventStore`.
- *API change*: `ActorContext.respond` has been renamed to `ActorContext.reply`.

## v0.2.1
- Dependency updates 
- Switched releases to new artifact versioning scheme. Artifacts now omit the `v` in the version string (e.g. `peloton-core_3-0.2.1`).

## v0.2.0
- Added support for remote actors. 
- Improved handling of scoped actor systems

## v0.1.0
- Initial release. Basic support for stateful and persistent actors