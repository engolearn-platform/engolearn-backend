# Placement / Grammar — integration with team dev, 2026-11-23

**Goal:** Preserve current Placement and Grammar work while adopting the team's authorization, runtime and Media contracts on the existing local feature branches.

**Authorization:** User approved fetching and merging dev into the existing branches. Never push or merge into remote main/dev. No new branches. Keep actual D: checkouts usable.

**Sources:** origin/main BE 0f41690 / FE 59078cc do not yet contain the new contracts. Requirements are present in origin/dev BE 96c7989 / FE 0e9adee. Read runtime-configuration, rbac-verification, user-auth, media/integration-guide and FE role-authorization from that ref. Runtime profiles are dev/prod, unrelated to Git branch names.

## Constraints

- Preserve all authored Grammar content (43 topics / 106 lessons / 530 questions), learner progress, published revisions, historical Placement attempts and audio.
- Leave drafts unpublished. No bulk reseed or deletion. Never commit local environment secrets or exported database snapshots.
- Keep team layouts and feature structure. Other modules enter through merge only; change their code only when an explicit shared integration requires it, and document why.
- User owns user_profiles.cefr_level through UserLevelPromotion; monotonic updates, no new parallel profile collection or profile upsert.
- Placement owns audio authorization; MediaManagement owns storage. Persist mediaId, resolve temporary delivery after ownership checks, do not persist delivery URLs.
- Full Maven verify and FE build/lint/auth checks are required. Distinguish existing failures, unit checks and real integration evidence.

## Tasks

- [ ] 1. Preserve local changes in local checkpoint commits after reviewing paths and secrets; record commit IDs. Merge origin/dev into current BE merge/placement-guest-user-main and FE fix/placement-guest-navbar. Resolve conflicts by preserving Grammar and new team contracts, inspect every resolution.
- [ ] 2. Audit service proxies and FE guards for both modules. Use service @PreAuthorize and auth/contract ProtectedRoute / HasRole. Verify Guest 401, USER 403, CONTENT_MANAGER inherited USER, ADMIN inherited management, cookie login/refresh/logout and direct navigation. Reuse team auth tests.
- [ ] 3. Trace Placement completion/automatic completion/Guest claim/learning activation into UserLevelPromotion and user_profiles. Reproduce reported dev discrepancy with real Mongo. Preserve joint transactions and max-level semantics; no mass historical backfill.
- [ ] 4. Integrate Placement audio with shared Media contract: model/reference compatibility, bank editing/upload/read, authorized attempt/review delivery and expiry refresh. Add explicit replay-safe preflight/migration preserving old snapshots and local dev playback. Prod must not depend on repository JSON/audio. Grammar currently contains text only; do not invent audio requirements.
- [ ] 5. Align dev/prod runtime with team configuration. Update ignored local .env privately, keep .env.example free of real credentials. Verify Mongo replica topology/ports without deleting volumes. Run prod JAR outside repo with explicit environment, HTTPS cookie policy, Mongo and Cloudinary; local HTTP dev remains supported. Do not claim other teams' module readiness.
- [ ] 6. Reconcile outdated tests against actual contracts, not by suppressing failures. Run scoped Placement/Grammar + auth/runtime/media tests, Maven verify, FE auth/build/lint and real FE proxy integration for both modules. Test current and historical audio, seeking, ownership, same/higher/lower profile promotion and Grammar pinned revisions.
- [ ] 7. Update owning docs and a short result report with paths, checks, outstanding limitations and local run instructions. Recheck Git status and refs. Do not push main/dev or private branches unless requested.

## Review focus

Guest audio must work without revealing answer keys; a foreign attempt must not resolve protected media. Expired delivery URLs must be refreshable through authorized feature reads. Interrupted uploads/migration must be replay-safe. Promotion must never lower a level or reset unrelated profile state. New service guards must not break internal scheduled finalization or legitimate owned Guest claim. Prod evidence must not silently reuse source-tree media, stale compiled classes or mocked HTTP.

## Execution record

Pending; check boxes indicate completed work only after verification.
