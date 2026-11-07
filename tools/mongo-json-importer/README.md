# Generic MongoDB JSON Importer

A domain-agnostic development tool for importing JSON datasets into MongoDB. It uses a tool-local .env for importer options and reads only shared MongoDB credentials/database defaults from the repository root .env. No application-specific schema or collection names are hard-coded.

## Quick start

Run commands from the repository root:

    Copy-Item .env.example .env
    Copy-Item .\tools\mongo-json-importer\.env.example .\tools\mongo-json-importer\.env

    # Check every JSON file in samples/; writes nothing
    .\tools\mongo-json-importer\import.ps1 -DryRun

    # Import every JSON file in samples/
    .\tools\mongo-json-importer\import.ps1

    # Check or import one specific JSON file
    .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\example.json -DryRun
    .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\example.json

When -File is omitted, all *.json files directly inside tools/mongo-json-importer/samples/ are loaded in filename order. Every input file is parsed and preflighted before the first write.

## Input format

Each file contains a top-level collections object. Each property name is a MongoDB collection name and each value is an array of documents:

    {
      "metadata": { "name": "team-samples", "version": 1 },
      "collections": {
        "users": [{ "_id": "user-001", "name": "Alice" }],
        "products": [{ "_id": "product-001", "name": "Keyboard", "price": 100 }]
      }
    }

- metadata is optional and ignored.
- collections is required.
- Collection names are dynamic; no application domain is assumed.
- Every document must have a non-null _id. IDs are never generated automatically.
- MongoDB Extended JSON supports ObjectId, Date, Decimal128, and binary values.
- By default, 24-character hexadecimal strings in fields named _id, ending in _id, or ending in _ids are converted to BSON ObjectId. Arrays in matching *_ids fields are converted element by element. Other strings are unchanged.
- Set MONGO_IMPORT_OBJECT_ID_FIELDS in the tool-local .env to a comma-separated list of exact field names or wildcard patterns. Use "none" to disable conversion. This lets datasets with string-based identifiers opt out or define their own conventions.
- The importer validates the JSON envelope, document shape, and duplicate input IDs. It does not validate domain schemas or cross-collection references.

### Datasets with string user IDs

Learning Path stores `user_id` as a BSON string, even when it contains 24 hexadecimal
characters. For mixed Assessment / Learning Path datasets, set the following in the
tool-local `.env` before both dry-run and import:

```dotenv
MONGO_IMPORT_OBJECT_ID_FIELDS=none
```

Use explicit Extended JSON (`{"$oid":"..."}`) for every field that must remain an ObjectId,
such as a Learning Path `_id` or question snapshot `path_id`.
Keep `user_id`, `source.assessment_attempt_id`, and `source.paper_id` as plain JSON text.
Assessment owns its native attempt IDs; preserve their original BSON representation in
`assessment_attempts._id`. Learning Path no longer requires `attempts` or `micro_skill_results`.
Disabling automatic conversion does not disable Extended JSON decoding. Inspect the full
dataset first: a bare 24-hex `_id` also remains a string with this setting.

The generic importer defaults and other datasets are unchanged; these settings must be
selected explicitly for the dataset being imported. See the
[Learning Path ID conventions](../../docs/technical/learningpath/learning-path-list-api.md#user-id-storage).

## Configuration

Importer-specific settings live in tools/mongo-json-importer/.env. Create it by copying tools/mongo-json-importer/.env.example. Shared connection settings remain in the repository root .env: MONGO_USER, MONGO_PASS, and APP_DB. The importer-local file can override the URI or target database.

| Variable | Default | Purpose |
|---|---|---|
| MONGO_IMPORT_URI | derived from root MONGO_USER / MONGO_PASS | Optional URI override reachable from Docker network |
| MONGO_IMPORT_DATABASE | root APP_DB | Target database |
| MONGO_IMPORT_CONFLICT_POLICY | skip | skip, fail, or replace |
| MONGO_IMPORT_OBJECT_ID_FIELDS | _id,*_id,*_ids | Field names/patterns whose 24-hex string values are converted to ObjectId; "none" disables |
| MONGO_IMPORT_BATCH_SIZE | 500 | Documents per bulk-write batch |
| MONGO_IMPORT_CONTINUE_ON_ERROR | false | Continue after a failed batch |
| MONGO_IMPORT_REQUIRE_CONFIRMATION | true | Require typing IMPORT before writes |

The default URI connects to the local Compose service mongodb. An override URI must be reachable from a temporary container attached to the Compose network.

## Conflict policies

- skip: keep existing documents with matching _id; insert only missing documents.
- fail: preflight all input and abort before writing if any _id already exists.
- replace: replace the entire existing document; old fields missing from the input are removed.

replace is destructive. Use -DryRun first and inspect the report.

## Safety and limitations

- -DryRun always performs read-only preflight and never writes.
- The tool refuses system databases and database names that look like production.
- All input files are preflighted before writes. A fail conflict therefore prevents all writes.
- MongoDB writes are batched, not transactional. If a later batch fails, earlier successful batches remain.
- MONGO_IMPORT_CONTINUE_ON_ERROR=true attempts later batches after a failed batch; it does not roll back successful writes.
- The importer does not infer dependency order or enforce foreign-key-like relationships. If documents depend on one another, order collection properties in the JSON accordingly.
- Input files must be inside the repository because the repository is mounted read-only into the temporary mongosh container.
- The tool starts only the mongodb Docker Compose service; it does not start the backend or Mongo Express.

## Troubleshooting

- Missing root .env: configure the project's MongoDB credentials. Missing importer .env: copy tools/mongo-json-importer/.env.example to tools/mongo-json-importer/.env.
- Docker unavailable: start Docker Desktop and retry.
- Empty samples/: add one or more JSON files or pass -File.
- Invalid JSON, missing collections, or missing _id: fix the input file before retrying.
- Import error: inspect the report. Earlier successful batches are not rolled back.

## Shared Assessment / Learning Path demo

The two datasets use account `pfc-demo-learner-v2`:

| File in samples/ | Collections / documents |
| --- | --- |
| `assessment-demo.json` | 8 / 11: demo account/profile, baseline/gate, version-2 paper, four historical attempts and Assessment profile |
| `learning-path-demo.json` | 4 / 766: 6 paths, 390 referenced question snapshots, 360 pool questions and 10 passages |

The Assessment file replaces `assessment-paper-v2.json`; keeping the old file alongside it
would duplicate the paper ID during multi-file preflight. No `attempts` or `micro_skill_results`
projection is included. Snapshot/path IDs remain ObjectIds; owner IDs are strings. The 210
orphaned local question snapshots are not part of the shared dataset.

### Import and initialize

Use a development replica-set database. Configure both the importer and backend for that
same database; the backend does not automatically load these datasets at startup. In the
importer-local `.env`, use `MONGO_IMPORT_OBJECT_ID_FIELDS=none` and
`MONGO_IMPORT_CONFLICT_POLICY=skip`. Import the two files explicitly:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-demo.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-demo.json
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-demo.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-demo.json
```

Inspect the reports and enter IMPORT when prompted. Skip preserves existing IDs, including
practice progress; reimporting does not reset dates, quota, cooldown, grants or ownership of
existing documents. Do not use replace against an existing learner database to force a replay.

Initialize the shared Subscription catalog on a fresh database using its
[existing seed](../subscription/README.md), then initialize only this demo account with
[the account-only manifest](../subscription/assessment-learningpath-demo-account.json).
The manifest is published through the backend, never passed to this importer. Existing
catalogs must not be republished or overwritten to add this account.

Run the backend with `dev` active, `prod` absent, and the existing Assessment audio directory:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.profiles.active=dev --engolearn.assessment.media-directory=./tools/mongo-json-importer/samples/assessment-media --engolearn.subscription.publish-manifest=tools/subscription/assessment-learningpath-demo-account.json'
```

After the account publication succeeds, omit the publish-manifest argument on normal restarts;
keep the profile and media directory. All 13 Assessment WAV files are already committed.

### Demo account and flows

Public development credentials: `assessment-demo@example.test` / `EngoLearn-Demo-2026!`.

1. Open `/assessment/history`, then `/assessment/attempts/demo-history-64/result` (64/100).
2. Open `/learning-paths`: the dataset has three completed paths, two cancelled paths and
   one active path at export time. All six belong to the same demo account. Only the path
   with a real matching source links to `demo-history-64`; five historical paths have no
   linked Assessment result. Their baseline scores and practice progress are preserved.
3. The 64-point source returns ALREADY_CREATED. To test recommendation and creation,
   complete a new Assessment. Eligible skills still require at least 30 ACTIVE pool
   questions at the result's level; this pool currently supports B1 only. Existing active
   skills are excluded. Confirm creation in the dialog, then practice the new path at week 1.
4. Subscription starts with ordinary Free access. Full skill display follows the normal
   Trial/Premium policy; importing data does not grant a paid subscription. Assessment's
   real cooldown and quota continue to apply.

Historical dates are fixed. Startup/scheduled lifecycle processing may close days, advance
weeks or cancel overdue active paths as time passes; importing with skip does not restart them.
**Learning Path Listening audio is not supplied:** its five audio URLs are example.com
placeholders. Question rendering, answers, grading and review can be demonstrated, but real
Listening playback cannot. Assessment audio is a separate, complete set; it must not be
substituted for unrelated Learning Path exercise audio.
