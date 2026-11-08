# Placement: run from a clean clone

The current full local implementation is on BE `merge/placement-guest-user-main` and FE `fix/placement-guest-navbar`. Latest commits can still be local-only; obtain the reviewed matching pair before using this runbook on another machine.

## Requirements

JDK 21, Node/npm, PowerShell and Docker Desktop running Linux containers. Maven
wrapper dependencies are downloaded on first build. No parent bank directory or
separate Node audio server is needed for new V2 attempts.

## Backend

From `engolearn-backend`:

```powershell
.\tools\placement\setup-local.ps1
# Review database/preflight output, then import only the missing Placement inputs:
.\tools\placement\setup-local.ps1 -Apply
.\tools\placement\run-local.ps1
```

If .env is absent, setup creates it from .env.example and generates a JWT secret.
Existing .env values are kept. Check host port, APP_DB and credentials before apply.
The default host MongoDB port is 27019; inside Docker it is mongodb:27017. Existing
Docker volumes retain their original users/passwords; this setup never resets them.
The root .env is ignored by Git and must not be shared with credentials filled in.

Setup checks actual replica-set topology, all packaged inputs/media and existing
document content. It imports exactly the 320 questions and one V2 DRAFT test set,
skips identical IDs, rejects different content and never writes placement_attempts.
An explicit -Apply is required for writes; a plain setup call is read-only for DB data.
Do not run the generic all-samples import to enable Placement.

The backend listens on 8080. run-local explicitly activates `dev,placement-local`:
Mongo Placement persistence, dev-only DRAFT access, media directory and registration
transactions live in application-placement-local.yaml. Do not put global
spring.profiles.active or Placement repository switches in .env: tests also read it.
Default tests therefore keep their original sample/slice settings. Real cookie
authentication remains enabled in the local profile; no fake user is supplied.

## Frontend

From `engolearn-frontend`:

```powershell
npm ci
npm run dev
```

Open http://localhost:3000. Vite proxies /api to backend 8080 by default. When using
a different backend port, set VITE_API_PROXY_TARGET before starting Vite.

## Listening and historical results

The repo contains 96 original checksum-named WAVs (about 48 MB) under
tools/mongo-json-importer/samples/placement-media. The V2 snapshot contains six
relative URLs under /api/v1/placement/media, served by BE with HEAD and byte-range
support. The media directory is configurable; only hash-named WAVs beneath its real
path are exposed. No directory listing, metadata, credentials or arbitrary paths
are served. Content-hash URLs can be cached; replace media only by adding a new hash.

V1 files/IDs/version and existing DB attempts are retained unchanged. New starts
choose the newest version of each code within the selected status; PUBLISHED still
wins over DRAFT. Attempt/result/review reads keep the exact assigned ID/version.
Old V1 snapshots contain localhost:8892 URLs: retain the previous audio server on
the original developer machine while those historical attempts need it. They are
not silently migrated or republished. Fresh team setup imports V2 only.

## Package verification and tests

```powershell
node tools/placement/build-dev-package.mjs --check
.\mvnw.cmd verify
```

The package check uses only checked-in seed/media and validates Mongo conversion,
20-question selection and all 96 WAV hashes. mongosh is used if installed, otherwise
Docker runs its offline validator. Only `--verify-authoring-source` additionally
requires the original external bank. See [tool details](../../tools/placement/README.md).
The immutable V1 input is intentionally not regenerated.

Historical project-wide test failures are recorded separately in
[merge verification](placement-merge-verification.md). New acceptance evidence for
the portable setup is recorded in placement-portable-verification.md after testing.

## Placement data layout after relocation (2026-11-09)

Source seed/schema/provenance files now live in `tools/placement/data/seed/`;
development sidecars live in `tools/placement/data/dev-v3/`. Generation, validation
and Placement persistence tests use these paths. The old backend-root `data/`
directory is no longer required. See [tool layout](../../tools/placement/README.md#data-layout-relocated-2026-11-09).

Current import inputs and all 96 WAVs remain in `tools/mongo-json-importer/samples/`.
This relocation does not migrate Mongo data or change question/snapshot/media
content. Parent `data/bank-320/` references in provenance remain historical external
authoring paths and are only needed by the optional authoring-source audit.

Verification and handoff details: [data relocation](placement-data-relocation.md).
