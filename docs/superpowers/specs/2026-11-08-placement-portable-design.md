# Portable Placement development setup

## Approved intent
The user approved making a clone runnable by each teammate before deploying any
shared server: ship media/data, isolate DEV settings from tests, serve media in BE,
provide setup commands, validate on a fresh database and share the integration branch.

## Approach
Chosen: commit all 96 checksum-named WAVs (about 48 MB) with existing 320 canonical
seed questions. Serve them as static public resources through the BE and FE API
proxy. A new immutable V2 DRAFT snapshot uses relative media URLs. Keep V1 inputs
and snapshots unchanged. Each test-set code selects its latest version within its
status; PUBLISHED keeps priority over dev DRAFT. Existing attempts still load their
exact ID/version. Existing V1 localhost:8892 URLs retain their legacy server until
those attempts no longer need it.

Alternatives: retaining the independent audio server preserves a fourth process
and machine-local paths; external media hosting adds deployment/account work before
local onboarding is reproducible. Neither meets the approved immediate goal as well.

## Constraints and decisions
- Reuse Spring MVC resource handlers for HEAD/Range; no new dependencies or controller
  envelope exceptions. Restrict public resources to real hash-named WAVs under a
  configurable directory; reject traversal and symlinks escaping it.
- Portable package checks use checked-in seed hashes and checked-in WAVs. Original
  authoring-bank verification remains optional and explicitly separate.
- Activate `dev,placement-local` explicitly when starting BE; root .env contains
  connection/secrets, not global Spring profile/repository switches.
- Setup preflights both exact input files, verifies existing content before skip,
  never replaces data, and preserves existing attempts and snapshots.
- No production publication, destructive DB migration or shared main merge without
  review. Push the integration branch when verification/review is recorded.

## Acceptance
An exported source tree with no parent data/bank-320 can validate its package,
import V2 on an empty replica-set database, run Guest/save/submit/auth/claim/result,
and stream all six snapshot audio files through FE. Regression tests pin latest
version selection and media path/range behavior. Report pre-existing suite failures
separately. Preserve the user's pre-existing application.yaml edit.
