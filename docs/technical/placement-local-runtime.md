# Placement shared data and local runtime

## Shared files

- `tools/mongo-json-importer/samples/placement-v3-questions.importer.json`: 320 DRAFT questions.
- `tools/mongo-json-importer/samples/placement-v3-test-sets.importer.json`: one DRAFT twenty-question snapshot.
- `data/placement/dev-v3/`: selection/audio/package manifests and preparation notes, never import inputs.

Run from the backend root:

```powershell
node tools/placement/build-dev-package.mjs
node tools/placement/build-dev-package.mjs --check
```

Generator and validator use the shared sample paths. Both relocated JSON payloads
match the committed originals exactly in parsed values, IDs and array order.
Generated JSON and manifest-hashed repository inputs are pinned to LF so Windows
checkout does not invalidate byte checksums. The seed manifest was refreshed
against the current source validation metadata; canonical source questions/audio
were not edited.

## Existing development database

On 2026-11-08, read-only inventory of the backend-configured database
`engolearn_placement_dev_v3` at `127.0.0.1:27019` found replica set `engolearn-rs`,
all 320 questions and one test set, with zero content mismatches and zero missing
IDs. It already contained 17 attempts. No dataset re-import or replacement was
necessary; no existing attempt was reset. Do not import every sample to enable
Placement: that also selects unrelated Topic/Assessment/Learning Path datasets.

For another developer's database, configure importer and backend to the same
target, use `MONGO_IMPORT_OBJECT_ID_FIELDS=none`, and dry-run each Placement file
explicitly with `-File`. Keep `fail` until existing documents have been compared;
`skip` alone does not verify their content. The PowerShell importer runs inside
Docker, where `mongodb:27017` maps to the local Compose service exposed on host
port 27019. Native mongosh must use the host endpoint, not the Docker hostname.

## Start local backend and frontend

Root `.env` is local and ignored by Git. Its existing connection/credentials are
preserved. Add these Spring properties to that file:

```properties
spring.profiles.active=dev
engolearn.placement.persistence.mode=mongo
engolearn.placement.test-set.allow-draft=true
```

Use a replica set and `engolearn.mongodb.transactions.enabled=true` for real
registration. The pre-existing application.yaml transaction change was preserved.
An optional configuration example is [runtime-config.example.properties](../../data/placement/dev-v3/runtime-config.example.properties).

```powershell
# Backend root: listens on 8080; automatically reads root .env
.\mvnw.cmd spring-boot:run
# Frontend root: Vite proxy defaults to backend 8080
npm run dev
```

Open `http://localhost:3000`. Backend startup was verified with the existing
application database and app credentials, Mongo Placement repositories and dev
DRAFT access. Guest -> save -> submit -> login -> claim -> result/review passed
28/28 real HTTP assertions through the frontend proxy. Verification registered
one real DEV test account and created one new attempt; no fake identity/result
was inserted.

## Listening server

From the parent `EngoLearnApp` directory:

```powershell
node .\data\bank-320\serve-runtime-audio.mjs
```

The existing server listens on 127.0.0.1:8892 and reads hash-named WAV files from
`data/bank-320/runtime-preview/audio/`. The backend returns their URLs from the
Mongo snapshot. Relocating importer JSON does not relocate or re-host the WAVs.
Native audible playback is a separate check from HTTP delivery.

For historical merge-suite results, see [merge verification](placement-merge-verification.md).
The current task's checks must be reported separately from those earlier results.

Audio HTTP verification on the same run passed for all six snapshot Listening
URLs: HEAD 200 with audio/wav, byte-range GET 206, CORS allowing the frontend,
and full-file SHA-256 matching each hash-named URL. The pre-existing server on
8892 was a temporary Node audio wrapper with wildcard public CORS; it was left
running unchanged. The repository command above is the repeatable startup option.
Native speaker output was not manually listened to during these HTTP checks.

## Verification of the shared-data migration (2026-11-08)

- Package generation and `--check`: PASS; actual importer input parsing and
  Placement content checks: PASS; 96/96 source/runtime WAV hashes match.
- Read-only importer preflight against the existing configured database: 321
  existing documents, zero missing, zero replacements, zero writes. Existing
  document content was compared before using skip for this read-only report.
- Real FE proxy -> BE -> configured DEV Mongo: 28/28 HTTP assertions PASS.
- Six Listening media URLs: HEAD, Range, CORS and SHA-256 PASS.
- ArchitectureTest: 9 checks PASS. Placement: 132 tests PASS.
- Complete isolated test run: 681 tests, 7 failures, 48 errors, 28 skipped.
  Every failing test identity also exists in the previously verified main
  baseline. Full suite remains failing; this migration adds no failing identity.

The local `.env` now enables dev Mongo repositories. A plain verify command reads
those local development switches too; the first such run had 11 failures and
17 errors, including five Placement slice-context errors from that configuration.
Run the repository's default test setup explicitly without altering the running
development backend:

```powershell
.\mvnw.cmd verify '-Dspring.profiles.active=default' '-Dengolearn.placement.persistence.mode=sample' '-Dengolearn.placement.test-set.allow-draft=false'
```

This selects the repository's original unit/slice defaults; it does not disable
tests or replace real integration evidence. The real HTTP acceptance above uses
the configured Mongo backend, real cookie auth and real registration transactions.
