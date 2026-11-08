# Placement V3 DEV package and preparation plan

> Historical preparation notes below retain the original proposal constraints.
> For the shared-data layout and current local runtime verified on 2026-11-08,
> follow [Placement local runtime](../../../docs/technical/placement-local-runtime.md).
> The standalone/27017 proposal below is not the active backend configuration.

## Current boundary

M3.1 creates local artifacts only. It does not approve a target, import data,
create/drop indexes, restart Backend, or start audio. M1 and all shared importer
files are immutable inputs. See the shared [format guide](../../../tools/mongo-json-importer/mongodb-json-importer-data-format-guide.md).

Proposed target: `127.0.0.1:27017 / engolearn_placement_dev_v3` (not yet approved).
Never substitute `engolearn_e2e_20261107` or a `placement_m30_race_*` database.
Run `tools/placement/inspect-dev-target.mongosh.js` read-only before any approval.

## Package

Generate/check using [the Placement tool README](../../../tools/placement/README.md).
`package-manifest.json` records input/output SHA-256, counts and validation scope.
The two `*.importer.json` files live in `tools/mongo-json-importer/samples/` and are the only import inputs. Manifests remain in `tools/placement/data/dev-v3/`. Selection, package and
audio manifests are sidecars, not Mongo collections and not generic importer inputs.

- Questions: exact 320 canonical M1 documents, unchanged order/values/nulls, DRAFT.
- Test Sets: one `PLT-V3-DEV-001-V1`, code `PLT-V3-DEV-001`, version 1, DRAFT.
- Items: 14 Reading then 6 Listening; A1/A2 = 4R1L, B1/B2 = 3R2L.
- Exact selection is locked in `selection-manifest.json`. All five B2 semantic
  families are different. Snapshot content comes only from canonical M1.
- The actual Java factory builds the snapshot and `MappingMongoConverter` writes
  Mongo field names: `order_index`, `correct_option_id`, `audio.duration_seconds`.
  Option/item identifiers are `id`, not renamed to `_id`. Root IDs stay String.
- Question audit timestamps are absent in M1 and remain absent; no historical
  timestamp is discarded. New snapshot timestamps are not invented. The shared
  importer bypasses Spring auditing; keep the real execution time in the operator log.

## Audio preparation (not hosting)

Canonical WAV files remain under the workspace's
`data/bank-320/package-placement-320-v3/batches/*-compact-v3/audio/`.
The 96 canonical files and existing hash-named hosting copies are checksummed.
No WAV is generated/copied, put in a JAR or staged for Git.

Existing server: workspace `data/bank-320/serve-runtime-audio.mjs`, bind
`127.0.0.1:8892`, `/audio/<sha256>.wav`, GET/HEAD/Range, `audio/wav`, CORS
`http://localhost:3000`. It reads hash-named copies in `runtime-preview/audio/`;
these audio bytes must match M1. No question content is read from runtime-preview.

`audio-url-manifest.json` resolves 96 URLs and the snapshot uses six of them.
Generator `--audio-base-url` configures the DEV origin. A different origin also
requires an appropriately configured server/proxy in a separately approved phase.
No playback success is claimed: server startup, HTTP 200/206 and audible native
`<audio>` playback remain later work. URL null simulation is not media validation.

## Database/environment approval

The proposed target must be absent or empty and explicitly approved before creation.
If it exists with data, inventory it; stop for unexpected/non-Placement data.
Native MongoDB is standalone 9.0.4. The separate runtime candidate is documented in
`runtime-config.example.properties`, using port 8082 and an externally supplied
`AUTH_JWT_SECRET`. It has not been activated or boot-tested.

All repositories/modules still use the shared MongoTemplate. Changing database
properties redirects the entire new Backend instance, not Placement alone. Do not
change the team's existing Backend configuration. Generic collection bootstrap is
disabled in the candidate; this does not disable all other module activity.

The `dev` profile normally enables Mongo transactions. The standalone candidate
explicitly disables the manager, but this does not make transaction-required Auth
registration work. An environment owner must approve a working Auth/User strategy
or a suitable separate replica-set environment. Do not add fake users, bypass
security, or change Auth/User/shared config to make this task pass. Changing the
proposed topology/URI requires a new inventory and approval.

DRAFT fallback requires `dev` and `engolearn.placement.test-set.allow-draft=true`;
PUBLISHED sets win over DRAFT. Use dev-only (never dev+prod) and verify there are no
unexpected PUBLISHED/pilot sets in the target. Do not publish this snapshot.

## Import transport prerequisites

`import.ps1` reads root `.env` and tool-local `.env`, then unconditionally executes
`docker compose up -d mongodb`, even with `-DryRun`. It accepts one `-File` string,
not an array; omitted `-File` imports unrelated samples. Its Docker URI must be
reachable inside that network. Container `127.0.0.1` is not the host.

Native Mongo already occupies host port 27017; Compose defaults to the same port.
Do not run that wrapper until root/tool env, port/network routing and its service
startup are separately approved. Do not edit the shared wrapper or Compose here.
For now: **`import.ps1 -DryRun: NOT_RUN — target not approved`**.

An execution option needing explicit team approval is native `mongosh --nodb --norc`
running the **same unmodified** `tools/mongo-json-importer/importer.mongosh.js`.
This avoids implicit Compose startup and supports both explicit input paths in one
preflight. `importer-native.example.env` documents its settings; it is not active
configuration. Unlike the PowerShell wrapper, the JS entry itself does not prompt
for confirmation. Do not mistake local input validation for database preflight.

## M3.2 commands: proposals only, do not run in M3.1

After written target AND native transport approval, use a dedicated PowerShell
session at Backend root, with no existing `MONGO_IMPORT_APPLY`. Set the URI only
after confirming the actual approved server. These commands are not an approval:

```powershell
$env:MONGO_IMPORT_URI = 'mongodb://127.0.0.1:27017/'
$env:MONGO_IMPORT_DATABASE = 'engolearn_placement_dev_v3'
$env:MONGO_IMPORT_OBJECT_ID_FIELDS = 'none'
$env:MONGO_IMPORT_CONFLICT_POLICY = 'fail'
$env:MONGO_IMPORT_BATCH_SIZE = '500'
$env:MONGO_IMPORT_CONTINUE_ON_ERROR = 'false'
$files = @(
  (Resolve-Path 'tools/mongo-json-importer/samples/placement-v3-questions.importer.json').Path,
  (Resolve-Path 'tools/mongo-json-importer/samples/placement-v3-test-sets.importer.json').Path
)
$env:MONGO_IMPORT_FILES_JSON = ConvertTo-Json -InputObject $files -Compress
Remove-Item Env:MONGO_IMPORT_APPLY -ErrorAction SilentlyContinue
mongosh --nodb --norc --quiet --file tools/mongo-json-importer/importer.mongosh.js
if ($LASTEXITCODE -ne 0) { throw 'Preflight failed; do not import' }
```

Review target name, 2 collections/321 documents, zero ObjectId conversions, conflict
counts and no unexpected existing data. Then, in a later approved write step:

```powershell
if ((Read-Host 'Type IMPORT only for the approved Placement DEV target') -cne 'IMPORT') {
  throw 'Cancelled'
}
$env:MONGO_IMPORT_APPLY = '1'
try {
  mongosh --nodb --norc --quiet --file tools/mongo-json-importer/importer.mongosh.js
  if ($LASTEXITCODE -ne 0) { throw 'Import failed; stop and inventory partial writes' }
} finally {
  Remove-Item Env:MONGO_IMPORT_APPLY -ErrorAction SilentlyContinue
}
```

Do not default to `skip` or `replace`. Re-import with `fail` aborts on any existing
ID before writes. For an interrupted import, compare existing document content to
the package before approving a missing-only retry. `skip` does not verify content;
`replace` destroys fields not present in input. No file/batch transaction is provided.

## Index preparation and old Attempts

Perform only after backup, approval and drained Placement writers. Existing
model annotations own the definitions; no new index definitions are invented here:

1. `placement_question_assembly`: status, skill, level, _id (nonunique).
2. `one_active_guest_attempt`: unique guest_session_id; partial user_id null,
   status IN_PROGRESS.
3. `one_blocking_user_attempt`: unique user_id; partial BSON string user_id and
   status in IN_PROGRESS/SUBMITTED/AUTO_SUBMITTED.
4. `one_reserved_start_session`: unique start_session_key; partial BSON string key.
5. Existing lookup indexes `placement_attempt_user_started` and
   `placement_attempt_guest_started`; built-in `_id_` on all three collections.

A new empty target has no Attempt backfill. If legacy data appears, do not repurpose
it automatically. Blocking unclaimed Guests need start_session_key from their
proven guest_session_id, with expected revision/ownership/status predicates and
revision increment. Resolve duplicates before index/backfill. Old User Attempts
did not record browser association; never guess a key. Drain/resolve them using
existing business rules and their pinned snapshots. Do not edit score/status/deadline
directly, delete attempts, or change ownership to bypass Policy A.

M2.7 startup fails for blocking Guests missing reservations. Old incorrect User
index options cannot be changed by createIndex with the same name; any targeted
drop/rebuild requires separate approval. No automatic migration or index drop.

## Backup, verification and rollback

Before writes: freeze Placement writers, record database/host/topology, collection
counts/index options, canonical/package hashes and IDs. Back up only approved
Placement collections as BSON plus metadata; record absent collections explicitly.
Do not back up/restore or drop the team's entire database. Standalone dump is not
a transactionally consistent snapshot without quiescing writers.

After import: check 320 questions (224R/96L, all DRAFT, String IDs, content equal
to canonical), one DRAFT Test Set, all 20 references and six audio URLs, correct
indexes, and zero seeded Attempts. Record actual operator time separately.

If a batch fails, successful earlier writes remain. Stop; compare IDs and content
instead of rerunning replace. Before runtime writes begin, a separately approved
targeted rollback can remove only newly inserted package IDs that still match
expected content, or restore the scoped backup. Validate restore in a recovery
database first. Do not drop the shared DB or unrelated collections.

After real Attempts exist, never blanket-delete questions/snapshots or restore over
new results. Freeze, retain versioned snapshots, reconcile new writes and prefer
a reviewed roll-forward. Do not roll back to pre-M2.7 writers while reservations
are unresolved. Audio/runtime activation and Guest/User validation are later phases.
