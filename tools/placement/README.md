# Placement data and local development tools

Start with [local runtime](../../docs/technical/placement/placement-local-runtime.md).

## Portable package

```powershell
node tools/placement/build-dev-package.mjs
node tools/placement/build-dev-package.mjs --check
```

These commands use the checked-in `tools/placement/data/seed/` and the 96 original
hash-named WAVs in `tools/mongo-json-importer/samples/placement-media/`. A clone
does not need its author's parent `data/bank-320/`. JDK 21, Node, Maven wrapper
dependencies and mongosh (or Docker Desktop for the validation fallback) are required.
The wrapper compiles models to run the real MappingMongoConverter/snapshot factory;
its compile step does not substitute for running the application test suite.

Outputs: shared `placement-v3-questions.importer.json` and
`placement-v3-test-sets-v2.importer.json`; sidecars remain in
`tools/placement/data/dev-v3/`. V1 `placement-v3-test-sets.importer.json` is retained
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

## Current team handoff

Use [the shared catalog bundle](../mongo-json-importer/samples/placement/README.md) for a new Placement namespace and [the runtime guide](../../docs/technical/placement/placement-local-runtime.md) to run EngolearnApplication and FE. The bundle includes 320 questions, 16 forms and 96 Cloudinary media references. Do not run the historical setup Apply or import old V1/V2 packages over this bundle.

The generator/setup files below remain for compatibility checks, reproducible source packages and legacy migration. They are not the current catalog importer. Existing snapshots and test dependencies are preserved; remove them only after a separate compatibility migration has been approved and verified.

## User resume acceptance

GET /api/v1/placement/attempts/current reads safe progress without creating a test. Contract, known side effects, real checks and repeat command: [placement-user-resume.md](../../docs/technical/placement/placement-user-resume.md).

## Data layout (relocated 2026-11-09)

- `tools/placement/data/seed/`: canonical source questions, schema and provenance manifests used by generation/tests.
- `tools/placement/data/dev-v3/`: generated selection/audio/package manifests and development notes; not importer inputs.
- `tools/mongo-json-importer/samples/placement-v3-questions.importer.json` and `placement-v3-test-sets-v2.importer.json`: legacy generator import inputs, not the current shared catalog bundle.
- `tools/mongo-json-importer/samples/placement-media/`: bundled WAVs served by BE.

The old backend-root `data/placement/` was relocated; runtime URLs, question IDs/content, V1/V2 snapshots, Mongo data and media remain unchanged. Paths beginning with parent `data/bank-320/` are historical authoring provenance outside the BE repository, used only by the optional authoring audit; they are not the removed BE data folder. Do not import seed/sidecar JSON directly or import every sample file.
