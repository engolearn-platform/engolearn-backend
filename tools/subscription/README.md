# Subscription operator setup

## Main development database (normal manual testing)

From the backend directory, keep the existing ignored `.env` credentials and add
`spring.profiles.active=dev` (also shown in `.env.example`). This profile enables
development identities and transactions. Default and production security are separate.

Run `docker compose up -d --wait --wait-timeout 180`. The main authenticated MongoDB
uses the existing `mongodb_data` and `mongodb_config` volumes and port 27017.
Its persistent internal key is generated inside the config volume, never in source.
The authenticated health check initializes `engolearn-rs` only when uninitialized
and waits for PRIMARY. Existing database users and application data are retained.
The replica advertises `localhost:27017` for the host backend; keep `MONGO_PORT=27017`.
Mongo Express uses a direct connection from its container. Never remove the volumes.

For the approved development baseline, start the backend **once** with:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--engolearn.subscription.publish-manifest=tools/subscription/dev-main-manifest.json'
```

The repository manifest is development-only: Free public plan, Free/Trial speaking
3600 seconds daily, proficiency Free 1/Trial 2 attempts monthly, and the three
existing development fixture IDs. It adds no paid offer, paid period or usage.
Use it only against the main development database after confirming the target;
publication runs atomically and repeated manifest IDs require identical contents.
Stop the backend after the first successful publication, then restart normally:

```powershell
.\mvnw.cmd spring-boot:run
```

Normal restarts use the persisted dev profile without running the publisher.
Open the frontend, select a development identity and check `/plans` and account
quota. Guest requests to account endpoints remain unauthorized; Trial activation
changes that selected account, so use a dedicated development fixture for it.

## Isolated operator environment

Supply an external JSON manifest matching `PublicationManifest` in Subscription request DTOs. It contains `manifestId`, an explicit `environment`, and mandatory `plans`, `policies`, `accounts` arrays. No default business limits or prices are embedded. Initial FREE and TRIAL policy scopes are mandatory and must already be effective. Every account requires explicit `NEW_ACCOUNT` or `MIGRATED` history provenance. Existing history is never replaced. Reusing a manifest ID accepts identical canonical content only.

Run the isolated opt-in replica runtime with `docker compose -p engolearn-subscription -f tools/subscription/compose.replica.yaml up -d`. This uses port 27018 and its own named volume. Never run a volume-removing command against existing environments.

Launch the application with profiles `dev,subscription-local` and property `engolearn.subscription.publish-manifest` pointing to the external JSON. Publication validates the entire manifest, establishes indexes, then commits configuration and history atomically. The runner is opt-in; omit the property on normal startup. No HTTP publication endpoint exists. Production requires approved configuration and a separate replica-set deployment.

For later Free updates use a strictly increasing version and next Vietnam local midnight as `effectiveAt`. Existing periods retain immutable snapshots. A scope can have one pending version. Delivered activation events apply the accepted MVP behavior; there is no missed-event replay.
