# Portable Placement verification — 2026-11-08

## Scope and source

Integration branch: merge/placement-guest-user-main, built on f1ef1cb. The user
approved team-local onboarding before any shared-server deployment. Existing
application.yaml transaction configuration is the user's prior uncommitted edit
and is excluded from this change's commit.

## Results observed

- 96 original WAVs (48,428,354 bytes) copied into repo, all SHA-256 verified.
- V1 shared input remains byte-for-byte identical to f1ef1cb. V2 is a new ID/version
  with relative BE media URLs; existing attempts retain exact version lookup.
- Package build/check succeeds from a staged-tree export named backend-clone,
  without .git, .env or a parent data/bank-320 directory. The generated sources
  and checksums no longer depend on the checkout directory's name.
- A missing .env in that export was initialized with a random JWT secret by setup.
- Fresh Docker MongoDB replica set at host 27028, database engolearn_portable_check:
  read-only preflight then apply inserted 320 questions and one V2 test set.
- Repeat apply: all 321 documents present/identical, no writes. A deliberately
  changed name in the exported input was rejected before generic importer writes;
  original file bytes were restored. The database's V2 name stayed unchanged.
- Exported BE ran on 8083 with dev,placement-local, real Mongo persistence and
  registration transactions. FE ran on 3001 with proxy to 8083; CORS for that
  temporary origin was explicitly enabled only in test launch arguments.
- 53/53 real HTTP assertions through FE passed: real registration,
  Guest save/submit, result denial before auth, login rejection/success, claim,
  result/review, idempotency, logout denial, score continuity and all six WAVs'
  same-origin path, HEAD, byte ranges and content hashes. No fake identity/result
  was inserted as integration evidence.
- Browser on the clean runtime started a Guest V2 attempt, opened question15,
  clicked play and observed the real audio element reach ended=true with
  currentTime=duration=6.443583, readyState=4 and error=null. Source was the FE
  /api/v1/placement/media URL, not localhost:8892. Screenshot retained locally.
- Default setup/apply also ran against the existing configured DEV database:
  existing 320 questions were skipped and only missing V2 was inserted; no V1
  replacement and no attempt writes were performed by setup.

## Tests and review

`mvnw.cmd verify`: 686 tests, 7 failures, 48 errors, 29 skipped. Every failed test
identity is present in the earlier main baseline; full suite is not green.
Placement has 137 tests, zero failures/errors, 1 skip; ArchitectureTest
has nine passing checks. The extra skip is the new warmed-resource symlink test:
Windows did not grant symlink creation privilege. Regular WAV/HEAD/Range/path
tests and latest-version tests passed.

RED evidence: media/version tests first failed compilation because implementation
did not exist. Clean export then failed on the old checkout-name assumption before
the portable path correction. Independent read-only review found a cache bypass
of containment checks; resourceChain(false) fixes it, with the platform-gated
symlink regression. Focused re-review found no remaining important blocker.

No new dependency or controller-envelope exception was added. Public media is
limited to hash-named WAVs beneath the real configured path; resolver containment
is checked on every request. The generic importer is unchanged.

## Operational boundaries

Use tools/placement/setup-local.ps1 and tools/placement/run-local.ps1; see
[onboarding](placement-local-runtime.md). DEV switches are in a dedicated profile,
not root .env, so default tests keep the original repository configuration.
The separate legacy 8892 server is still required only for existing V1 URLs.
This work does not deploy shared infrastructure or publish DRAFT content.
Repository-wide baseline failures and the earlier backend-role policy gap remain
outside this onboarding change. Integration branch is prepared for team review;
no merge into shared main is performed automatically.

## Delivery

BE code commit b76174a and FE commit f1737cc were pushed to origin on branch merge/placement-guest-user-main in their respective repositories. Team members can fetch these branches now. Review/merge into shared main remains a separate action. Local BE8080, FE3000 and legacy V1 audio8892 remain running; disposable clean-runtime services were stopped.
