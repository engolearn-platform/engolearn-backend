# Placement data and local development tools

Start with [local runtime](../../docs/technical/placement-local-runtime.md).

## Portable package

```powershell
node tools/placement/build-dev-package.mjs
node tools/placement/build-dev-package.mjs --check
```

These commands use the checked-in `data/placement/seed/` and the 96 original
hash-named WAVs in `tools/mongo-json-importer/samples/placement-media/`. A clone
does not need its author's parent `data/bank-320/`. JDK 21, Node, Maven wrapper
dependencies and mongosh (or Docker Desktop for the validation fallback) are required.
The wrapper compiles models to run the real MappingMongoConverter/snapshot factory;
its compile step does not substitute for running the application test suite.

Outputs: shared `placement-v3-questions.importer.json` and
`placement-v3-test-sets-v2.importer.json`; sidecars remain in
`data/placement/dev-v3/`. V1 `placement-v3-test-sets.importer.json` is retained
unchanged and is not a generator output. New V2 audio URLs are relative
`/api/v1/placement/media/<sha256>.wav`, so FE proxy and BE work on each machine.
Optional `--audio-base-url` accepts a root-relative prefix or an HTTP(S) origin;
an explicit origin retains the historical `/audio/` URL convention.

## Optional authoring-bank audit

```powershell
node tools/placement/build-dev-package.mjs --check --verify-authoring-source
```

Only this audit requires the original parent `data/bank-320/package-placement-320-v3`.
`build-mongo-seed.mjs` remains the original authoring tool. Portable checks verify
the committed seed-output checksums, snapshot content and all 96 packaged WAVs.
They do not claim to contact MongoDB or play sound.

## Setup and run

```powershell
.\tools\placement\setup-local.ps1
.\tools\placement\setup-local.ps1 -Apply
.\tools\placement\run-local.ps1
```

Setup initializes a missing local .env with a random JWT secret, ensures the Compose
replica set is healthy and performs input/content preflight. An existing .env is
never overwritten. No -Apply means no data writes. Existing identical IDs are skipped;
different content is rejected. The generic importer is not modified.

`-NoStartMongo -Network <network> -MongoUri <uri> -Database <name>` is available
for an explicitly selected isolated DEV/test replica set. Do not put credentials
in committed command examples or terminal logs. All input mounts are read-only.
