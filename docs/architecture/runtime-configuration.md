# Runtime configuration contract

## Layers and responsibilities

Only `dev` and `prod` are supported application runtime profiles. Select exactly one
explicitly. `application.yaml` is a shared configuration layer, not a profile.
The normal application entry point rejects missing, combined and unknown profiles
before beans or startup runners execute. Do not store `spring.profiles.active` in `.env`.

| Layer | Owns |
| --- | --- |
| application.yaml | Shared application configuration, local Mongo connection defaults, enabled transactions, Placement Mongo/level-sync wiring, common auth and integration properties |
| application-dev.yaml | Development resources, HTTP cookie defaults, optional test helpers, bundled media paths and VNPay sandbox defaults |
| application-prod.yaml | Managed MongoDB connection URI, application database, Render-aware HTTP port, CORS and a production-appropriate server-selection timeout |

Both environments require a replica-set-capable MongoDB deployment because business flows use
transactions. `dev` uses the team local single-node replica set; `prod` uses a managed replica
set such as MongoDB Atlas. The production URI owns the connection topology and credentials.
Use separate databases, credentials and provider resources; never share development data with
production. Both use real cookie/JWT authentication. Profile selection must not choose a subset
of business modules or weaken their rules.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
.\mvnw.cmd verify
java -jar target/engolearn-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

The entry-point initializer enforces profile selection, Placement Mongo mode and enabled
transactions; prod additionally requires deployment inputs and secure cookies. Focused
unit/slice tests may construct small contexts and explicit sample adapters; they do not
certify application runtime readiness. CI defaults to prod with disposable infrastructure
and test-only inputs, not production resources. Tests that explicitly select profiles or
override properties retain their current behavior; see [CI verification](../runbooks/ci-verification.md).
Maven profiles `dev-verification` and `full-integration` remain separate build/test selectors.

## Environment inputs

Use [.env.example](../../.env.example) for development. Never commit filled credentials.
Production may supply process environment or a deployment secret store. Existing ignored
`.env` files are not rewritten and are still imported by Spring. Do not copy a developer
`.env` into a deployment or reuse development credentials in prod.

| Environment input | Meaning / policy |
| --- | --- |
| MONGO_HOST, MONGO_PORT, MONGO_REPLICA_SET | Local `dev` Mongo address and replica set; defaults are localhost:27017 and engolearn-rs. Not used by `prod` when a URI is configured |
| MONGODB_URI | Required in `prod`; complete managed MongoDB connection string, normally the `mongodb+srv://` URI copied from Atlas. Keep it in the deployment secret store, never in Git |
| PORT | HTTP listener port; Render Web Services supply `10000` by default. `prod` falls back to `8080` outside Render |
| APP_DB | Application database name in both profiles; `spring.mongodb.database` overrides any database name embedded in the URI |
| APP_USER, APP_PASSWORD | Local `dev` application database credentials; Atlas credentials are supplied inside `MONGODB_URI` |
| MONGO_USER, MONGO_PASS | Compose/tool root credentials, not the application identity |
| AUTH_JWT_SECRET, AUTH_JWT_ISSUER | Real JWT configuration; existing signing-key validation applies |
| AUTH_COOKIE_SECURE | Dev HTTP/HTTPS override; prod requires secure cookies regardless of this alias |
| CORS_ALLOWED_ORIGINS | Frontend origins; explicit value required in prod |
| PLACEMENT_MEDIA_DIRECTORY, ASSESSMENT_MEDIA_DIRECTORY | Dev-only bundled-media overrides; not production inputs |
| CLOUDINARY_URL or CLOUDINARY_* | Shared Media provider credentials as required by the module |
| VNPAY_ENABLED, VNPAY_* | Optional provider activation/settings; enabled provider needs valid merchant settings; prod has no implicit sandbox URLs |
| ASSEMBLYAI_API_KEY, GEMINI_API_KEY, CARTESIA_API_KEY, CARTESIA_VOICE_ID | Speaking provider credentials and voice; optional SPEAKING_ASSEMBLYAI_*, SPEAKING_GEMINI_* and SPEAKING_CARTESIA_* inputs override shared endpoints, models, API version and adapter timeouts |

Root [Compose](../../docker-compose.yaml) provisions one member in engolearn-rs. Its
healthcheck rejects existing multi-member topology without modifying it. Host clients
must use advertised localhost:27017; the fresh-clone template now uses 27017. An existing
`.env` using 27019 needs manual port alignment before normal replica discovery. Do not
delete volumes or reset users to apply this change. Applications deployed in containers
or on other hosts need a resolvable advertised member address; localhost development
Compose is not a production deployment template.

## Development helpers

Helpers are opt-in. Prod excludes reset and seed beans by profile, and DRAFT fallback
checks dev without prod independently of YAML.

| Environment input | Default | Capability |
| --- | --- | --- |
| PAYMENT_DEV_RESET_ENABLED | false | Owner-only unresolved unpaid purchase release; preserves financial/refund guards, transaction and audit |
| PLACEMENT_ALLOW_DRAFT | false | DRAFT fallback only if no PUBLISHED test set exists |
| DEV_LOG_LEVEL | INFO | Development application logging; do not expose credentials/tokens |

Helpers never bypass identity, ownership or financial eligibility. See
[payment reset](../runbooks/dev-purchase-reset.md). Topic and Subscription development
content is supplied through explicit Mongo JSON importer files, not an application startup
seed or runtime flag. Use only the scoped `-File` commands in the
[importer guide](../../tools/mongo-json-importer/README.md) after confirming a disposable
local development target. The importer rejects system database names and names with a bounded
`prod`/`production` token; that name check does not establish that a database is safe or
non-production.

Safe defaults (helper flags false and secure cookies)
are declared once in application.yaml. Dev overrides only its permitted conveniences;
prod does not repeat those declarations. Profile guards still exclude dev beans in prod.

## Required production data and media integration

Every module MUST store business content/state in its owning Mongo collections and
durable media references in those documents. Audio/image/video bytes MUST be stored
through the shared Media storage capability; do not read production assets from the
source tree, packaged samples, localhost content URLs or a module-local filesystem.
Features persist mediaId, then authorize and resolve delivery through MediaManagement.
Do not persist expiring delivery URLs. See the [Media integration guide](../technical/media/integration-guide.md).

Production no longer accepts media-directory inputs as a deployment requirement.
The legacy Placement filesystem route is not registered in prod. The existing
Assessment filesystem reader refuses prod access with 503 MEDIA_UNAVAILABLE until
its owner integrates storage. This is an explicit incomplete integration, not a
replacement implementation or a claim of working production playback.

Each module owner MUST complete this checklist before marking the module VERIFIED:

- [ ] Remove production reads of repository JSON, sample media and filesystem directories.
- [ ] Persist business data in Mongo and media bytes through shared storage; persist mediaId references.
- [ ] Provide explicit preflight/import/migration with replay handling and recovery for partial uploads.
- [ ] Preserve historical attempt/result snapshots and old media availability during migration.
- [ ] Enforce approved Guest/User access and ownership before resolving protected content.
- [ ] Run the JAR outside the repository without tools/, source-tree resources or directory variables.
- [ ] Verify published content, playback/seeking, persistence and the module's real business flow.
- [ ] Keep missing content/storage failures explicit; do not use sample fallback to produce passing evidence.

## Module integration checklist

Each maintainer must record canonical property keys, environment mappings, purpose,
required/default values, owner, environment differences and verification commands. For
helpers also record authorization, prod exclusion and removal conditions if temporary.
Verify actual business flows, persistence and authorization; startup alone is insufficient.
Do not restore module-named runtime profiles.

| Area | Current integration responsibility |
| --- | --- |
| Topic | Development examples are imported from explicitly selected JSON files; keep learner/authoring fixtures out of production and verify publication, list, detail and attempt flows against the target database |
| Placement | Mongo/transaction/level-sync defaults configured. MEDIA NOT PROD-INTEGRATED: migrate local audio to Media storage and persist references in Mongo; verify current and historical playback without repository files |
| Subscription / Payment / Quota | Shared Mongo and transactions; import development catalog only to a disposable local database; verify Admin updates, provider and durable delivery independently |
| Speaking | Managed-only conversation and module wiring are active in both `dev` and `prod`. Provider credentials are supplied per capability and failures remain explicit. Production readiness is still unverified: provider-backed E2E, MediaManagement media-ID integration, multi-instance recovery/state coordination and load/latency evidence remain open |
| Assessment | NOT FULLY PROD-INTEGRATED: real baseline integration and storage-backed audio are required. Filesystem playback is refused in prod; verify preparation/attempt/review after integrating Media |
| Auth / Media / others | Verify real identity/authorization and storage integration in both profiles; no production dependency on repository samples or local media directories |

The [Speaking module contract](../technical/speaking-ai/module-contract.md#runtime-configuration)
owns the provider settings, environment mappings, defaults and verification commands for both profiles.

These gaps do not establish production readiness. Retired module-specific application
files must not be restored; scripts select dev. The old
tools/subscription/compose.replica.yaml is a legacy isolated fixture utility, not the
supported team runtime. Use root Compose and common environment inputs.

## Acceptance evidence

- Both normal runtime profiles select Mongo persistence and enabled transactions.
- The local `dev` database reports one member and a writable primary; the managed `prod`
  database reports a writable replica-set primary. Disposable rollback checks prove
  transactional behavior without using production data.
- With prerequisites, dev/prod start and auth, Placement, Subscription and Media smoke
  checks pass; unresolved module/provider checks are listed separately.
- Prod has no reset or automatic dev seed runners even with helper switches supplied;
  DRAFT fallback is unavailable.
- Prod starts without directory variables and cannot serve local Placement/Assessment media;
  module readiness requires storage-backed playback, not simply successful startup.
- No active command selects retired runtime profiles. CI defaults to prod; module owners
  must still make their integration suites prod-compatible and supply runtime evidence.
