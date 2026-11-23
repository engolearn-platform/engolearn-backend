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
The fresh-clone host MongoDB port is 27017, matching the advertised replica member;
inside Docker it is mongodb:27017. Older .env files using 27019 need manual port
alignment; setup does not overwrite them. Existing
Docker volumes retain their original users/passwords; this setup never resets them.
The root .env is ignored by Git and must not be shared with credentials filled in.

Setup checks the replica set, packaged media and existing content. It imports the
320 base questions and the V2 draft. Apply then adds the Vietnamese explanations
and a new V3 draft. Existing V1/V2 snapshots and placement_attempts are preserved.
An explicit `-Apply` is required for writes. Use the Placement setup for these inputs.

For a database that already has the question bank and V2, apply only the
explanation revision:

```powershell
.\tools\placement\setup-local.ps1 -ExplanationsOnly
.\tools\placement\setup-local.ps1 -ExplanationsOnly -Apply
```

This updates explanations only when the question and old explanation still match
the seed. It preserves Admin-authored edits and stops on a conflicting V3. Future
dev-only starts use V3 if no published set is available. For production or an
existing published set, use normal Admin versioning and publication to release the
updated explanations. Each attempt reviews the explanation stored in its own snapshot.

The backend listens on 8080. run-local explicitly activates `dev`. Both profiles use
Mongo Placement persistence and transactions from application.yaml. For the imported
DRAFT package set PLACEMENT_ALLOW_DRAFT=true in the ignored local .env before starting;
the default is false. Dev supplies the bundled media directory. Do not put
spring.profiles.active in .env. Real cookie authentication is used; no fake user is
supplied. See the [runtime configuration contract](../architecture/runtime-configuration.md).

The filesystem media route is development-only and is not registered in prod. Production
requires Placement integration with shared Media storage and durable Mongo references;
passing a local directory is not a supported production workaround.

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
not silently migrated or republished. Fresh setup starts from V2 and adds V3 with
the same portable media URLs and improved explanations.

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

## Team dev integration — 23/11/2026

Current authorization, dev/prod runtime, Cloudinary migration, local port handling and actual verification are maintained in [Placement/Grammar integration](placement-grammar-dev-integration.md). This supersedes older module-profile commands and the historical exact-USER-only learning activation restriction.
