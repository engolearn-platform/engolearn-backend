# Placement data relocation — 2026-11-09

## Scope

Moved all 10 files from backend-root `data/placement/{seed,dev-v3}` into
`tools/placement/data/{seed,dev-v3}`. No BE-root data directory remains.
Updated generation, optional authoring output, offline validator, persistence
test path, JSON line-ending attributes and documentation. Regenerated the
package manifest to record new source paths and the seed-manifest checksum.

Import inputs remain under `tools/mongo-json-importer/samples/`: questions and
V2 test set are the current setup inputs; V1 is retained for historical compatibility.
WAV files stay in `samples/placement-media/`. Seed arrays and importer envelopes
have different roles; never import all samples or seed/sidecar JSON together.
Parent `data/bank-320/` references in provenance are external authoring history,
not references to the removed backend-root folder.

## Observed verification

- Package BUILD and subsequent `--check`: PASS, including real Mongo converter,
  snapshot factory, importer parsing, 8 negative input regressions, 96 WAV hashes.
- 103 protected file checksums unchanged: 3 importer files, 96 WAVs, canonical
  questions/schema/audio manifest, and pre-existing local application.yaml.
- DEV preflight: 321 existing documents, 0 missing, 0 mismatches; no DEV writes,
  no attempt touched. Tests can write only their own test databases.
- Real FE proxy to IDE-started BE: GET current 200; audio Range GET 206.
- Required `mvnw.cmd verify`: still fails overall, 690 tests, 7 failures,
  48 errors, 29 skipped. The exact 55 failing identities match the previous
  level-integration run; no new failures. Placement: 141 tests, 140 pass,
  1 existing Windows symlink skip; changed Mongo persistence suite: 11 pass.
- Local handoff Markdown links checked: 354 inventory links and 2 guide links.

Not rerun: optional external-authoring audit, browser full submit/claim sequence
or FE build/lint (FE source unchanged). No new dependency or module business
change in the relocation itself. Later pre-push review fixed Placement submission
revision handling and FE terminal reconciliation; see placement-prepush-audit.md.
The user approved delivery on the existing feature branches only, with baseline
failures disclosed; no main merge/push is authorized. Source and destination data
paths must be staged together to preserve the move when handing off.

Startup remains Run EngolearnApplication (`dev,placement-local`) + npm run dev,
with Docker Mongo running. No reimport is required for this file relocation.
