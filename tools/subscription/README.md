# Subscription operator setup

Supply an external JSON manifest matching `PublicationManifest` in Subscription request DTOs. It contains `manifestId`, an explicit `environment`, and mandatory `plans`, `policies`, `accounts` arrays. No default business limits or prices are embedded. Initial FREE and TRIAL policy scopes are mandatory and must already be effective. Every account requires explicit `NEW_ACCOUNT` or `MIGRATED` history provenance. Existing history is never replaced. Reusing a manifest ID accepts identical canonical content only.

Run the isolated opt-in replica runtime with `docker compose -p engolearn-subscription -f tools/subscription/compose.replica.yaml up -d`. This uses port 27018 and its own named volume. Never run a volume-removing command against existing environments.

Launch the application with profiles `dev,subscription-local` and property `engolearn.subscription.publish-manifest` pointing to the external JSON. Publication validates the entire manifest, establishes indexes, then commits configuration and history atomically. The runner is opt-in; omit the property on normal startup. No HTTP publication endpoint exists. Production requires approved configuration and a separate replica-set deployment.

For later Free updates use a strictly increasing version and next Vietnam local midnight as `effectiveAt`. Existing periods retain immutable snapshots. A scope can have one pending version. Delivered activation events apply the accepted MVP behavior; there is no missed-event replay.
