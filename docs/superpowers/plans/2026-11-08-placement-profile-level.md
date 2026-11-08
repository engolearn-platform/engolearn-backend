# Placement -> User level integration — 2026-11-08

User explicitly approved completing Placement/Profile/Topic integration, inline notes for collaborators, existing local branches only, no push/main modifications. Fresh fetch: BE/FE behind0; mainBE0f41690, mainFE59078cc. Existing BE application.yaml stays excluded.

## Selected design and ownership

- Keep original Placement engines, User registration/profile reads, and Topic logic unchanged. Add a narrow User-owned UserLevelPromotion capability; only User repository updates user_profiles.cefr_level. No cross-feature repository/entity access.
- Promotion is atomic max(current,requested) using explicit A1,A2,B1,B2,C1,C2 ranks, not string comparison. Missing/malformed profile fails; no upsert. Update only cefr_level/updated_at, never save entire profile or reset learning progress. Same/equal/lower request performs no write.
- Mongo-mode wiring adds cohesive primary deadline/claim decorators around the old services. Original constructors and sample-mode behavior remain. Inline comments identify why the new boundary exists; no commented-out old implementation. Git commit is the rollback boundary.
- A shared Placement transaction executor creates/retries only the outermost Mongo transaction; nested calls execute within it without an extra interceptor. Retry transient/unknown-commit errors with fresh reads. This prevents duplicate-key recovery in an aborted transaction or a caught domain exception poisoning the enclosing claim transaction.
- User finalization/auto-finalization promotes profile in the same transaction as final attempt write. Guest finalization does not promote; successful authenticated claim promotes the actual result owned by that User. USER_HAS_RESULT promotes the existing User result, not the discarded Guest result. Repeats are monotonic/idempotent.
- Existing result/review GETs do not backfill already-final historical profiles. They may still resolve an overdue attempt under existing policy, and a real finalization transition then promotes its owner. Explicit POST /attempts/{id}/learning validates an owned valid result and promotes for historical results; request body cannot choose a level.
- Bắt đầu học awaits successful learning POST, then navigates to /topics. Failure keeps the result page with a retry message. Topic fetches its existing persisted profile level; no Topic code changes.
- Register remains A1. Exact CurrentUser role USER required on the new command (authorities are hierarchical). New dependency Placement->user.contract.UserLevelPromotion is approved by this task and recorded in the dependency table.

## Implementation / verification plan

1. Observe real API RED for the missing learning command on an existing V2 test result; no fake responses.
2. Add User contract/failure/repository/impl, internal transaction port+Mongo executor, primary decorators/config, learning command/service/DTO and scoped error translation. Add short integration comments and this handoff doc.
3. Enable CTA and docs. Do not alter existing Topic business rules, question data, results or profile schema.
4. Real Spring/Mongo integration tests: real registration and canonical Placement snapshot, C1/C2 preserved, concurrent monotonic updates/retry, joint rollback then successful submit. Real FE proxy acceptance: historical promotion, direct User submit, Guest claim, ownership denial, repeated commands and Topic viewer level. No mocks/fixture responses as integration proof.
5. FE build/lint/auth checks, BE verify/ArchitectureTest, compare known55 baseline failure identities; browser topic navigation with updated level. Inspect status/main refs, commit locally only. Document actual pass/fail/unrun and data effects.

## Review rulings

- Fresh reviewer identified Mongo transaction abort/retry and nested rollback-only risks; programmatic outermost executor addresses both. Query construction remains in User repository, policy in User capability.
- Canonical packaged Placement data is used as real input in isolated replica-set tests; test Users are registered via real HTTP. No synthetic API responses or mock identities.

## Execution record

Implemented and checked below. Existing local branches: BE merge/placement-guest-user-main, FE fix/placement-guest-navbar. No new branches.

## Actual verification / handoff

- Main fetched at start and before finalization: BE0f416900f9e6b785682a5073c5bac84f6eee5b08, FE59078ccc15799ca93288078d0642a62f03cf9030; behind0 in both. No merge/push/main edits and no new branches. Main checks are read/fetch, not automatic overwrites of local work.
- FE build pass5.00s, lint0 errors/72 prior warnings, auth return-to4/4 pass. Real browser test account: owned A2 result -> Bắt đầu học -> /topics shows CEFR A2 and existing A2 catalog access. Screenshot placement-level-topics.png. Topic source diff is empty.
- Real FE3000->BE8080->Mongo acceptance84/84 pass, including User A1->A2 on submit, Guest profile A1 until claim thenA2, four concurrent claims, repeated learning commands, foreign/anonymous denial and client-supplied C2 ignored. A discarded Guest B2 result did NOT promote a User whose existing valid result is A2. Historical A2 result reconciled through learning POST.
- Four new full Spring/HTTP/real Mongo replica-set integration tests pass: C1/C2 never lowered by A2 learning activation (same/lower writes preserve timestamps); genuinely competing transactions cause a counted retry and finish monotonically; forced outer rollback leaves attempt/profile unchanged then real submit commits both; learning rejects an in-progress attempt without submitting/promoting. Canonical unchanged importer snapshot is the real test input; Users register through actual HTTP. No mocked API, fixture identity or invented result is integration proof.
- ArchitectureTest9/9 pass. Required final mvnw verify remains failing:690 tests,7 failures,48 errors,29 skips. Exact55 failure identities match existing main baseline; no new failure identities. Placement141 tests:140 pass,1 Windows symlink privilege skip. No checks weakened or old engines/tests changed.
- Fresh design/code review found transaction-abort/nested rollback risks and final-only command gap. Programmatic outermost transaction retry and explicit final/ownership guard address them. The User public capability wraps internal storage/conversion failures as its stable unavailable failure. No runtime dependency added.

## Where collaborators should review

| Boundary / owner | Files | Reason |
|---|---|---|
| User | new contract/UserLevelPromotion, contract/exception/UserLevelPromotionException, repository/UserLevelPromotionRepository, service/impl/UserLevelPromotionImpl | Existing profile only, atomic explicit-rank field update; same/higher is unchanged, no profile upsert or progress reset |
| Placement transactions | new config/PlacementLevelIntegrationConfig, PlacementTransactionExecutor, MongoPlacementTransactionExecutor | Mongo-only primary decorators preserve old engines and retry whole failed transactions; nested claim/deadline work joins one boundary |
| Placement finalize/claim | new PlacementLevelSyncDeadlineServiceImpl, PlacementLevelSyncClaimServiceImpl, PlacementUserLevelSynchronizerImpl | Sync only a real finalization transition or actual result owned by authenticated claimant; old constructors/engine code unchanged |
| Placement historical command | new PlacementLearningController/Service/Impl/Response; scoped PlacementExceptionHandler addition | POST validates existing owned final result and updates through User contract; failures remain retryable |
| FE Placement | PlacementResultCard + placement.service | Await successful command, guard duplicate click/unmounted navigation, then /topics; show error and retain result if command fails |
| Topic / existing User | unchanged Topic code, UserRegistrationServiceImpl, UserProfile model, UserLevelLookup | Existing consumers read persisted level normally; no extra parallel learning rules |

Short inline comments identify integration rationale at CAS, transaction joining/wiring, Guest claim result selection, explicit historical command, and FE navigation. Old code is retained as live delegates, not copied into commented-out blocks.

## Data / limitations / rollback

- No migration, seed replacement or mass historical backfill. Runtime validation created6 dedicated DEV test accounts and6 attempts across two acceptance runs (IDs in level-live-results-first.json and level-live-results.json). Existing dedicated resume-ui test profile was promoted to A2 through the explicit command. No personal User session logout or unrelated answer/progress edit. Isolated Testcontainers data is separate from DEV and cleaned by its test lifecycle.
- Already-final historical results update the profile when their owner presses Bắt đầu học. Ordinary GET of an already-final result/review does not backfill it. GET may still resolve a newly elapsed attempt under existing deadline policy; an actual owned finalization then promotes in its transaction.
- Mongo replica set plus mongoTransactionManager is required, already enabled by dev,placement-local. Sample/in-memory wiring keeps existing demo engines and has no real profile-sync/learning command; team startup must use the documented local Mongo setup. application.yaml's pre-existing user edit remains unstaged/excluded.
- Real15-minute automatic timeout was not waited out in this run; existing deadline regression tests pass and the new resolve transition was source-reviewed. Missing/malformed historical profiles and UI network-error message were not deliberately corrupted/simulated in the browser. Such profile failures fail closed; no auto-created profile or false navigation.
- Temporary cookie-isolated3002/8083 UI services are stopped afterward. Original3000/8080 services remain. No push at all in this task.
- Rollback before sharing: revert the paired local integration commits (BE and FE), preserving user's YAML diff. They restore prior wiring/disabled CTA. Do not undo committed team history or reset DB volumes. Max-level updates already committed are not automatically downgraded by reverting code; any data correction needs separate scoped review.

## Repeat checks

`mvnw.cmd -Dtest=PlacementUserLevelIntegrationTest,ArchitectureTest test` exercises the isolated real Mongo transaction boundary. `PLACEMENT_LIVE_ALLOW_WRITES=1 python tools/placement/check-profile-level.py` runs new disposable local users through FE/BE. Optional historical case requires all3 PLACEMENT_HISTORICAL_EMAIL/PASSWORD/ATTEMPT settings; no historical credentials are hard-coded. The Python script is optional standard-library verification, not a runtime dependency.

Evidence outside Git in C: workspace: level-red.log, level-live-results*.json, level-be-verify-final.log, level-be-comparison.json, level-integration-test.log, level-fe-build.log, level-fe-lint.log, level-auth-tests.log, placement-level-topics.png.
