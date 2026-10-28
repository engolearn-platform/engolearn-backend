# Slice C - review and retry handoff

Date: 2026-10-28. Branch: `feat/complete-topic-learning-flow`. Scope follows the user's latest decision: hide/unhide during a session is unsupported and untested; no skip/reconciliation/MAJOR rule is added. Existing visibility denials remain. Parent owns final real-HTTP/browser acceptance and OpenSpec status.

## Authority and session replacement

`topic_retry_runtime` contains one document per learner/topic, identified by a deterministic owner/topic key. It stores the current frozen session, current running retry attempt, completed item IDs, and currently official completed retry result for each item. Advance, answer, access, explicit next-item start and replacement all use the same version-checked CAS. Final correctness writes the accepted answer, completed snapshot, official per-item result, session cursor and receipt together; a late answer from an abandoned generation cannot publish after replacement.

LEARN remains in its existing collection and keeps the Slice B commit protocol. Catalog/progress/result/review overlay the official retry result only after the retry item completes. A lower score still replaces the previous result; unfinished retries do not change official completion/score. Replacement preserves results already published by completed items.

`topic_retry_locators` contains immutable identity/start-response metadata, prepared before CAS. Locators do not publish an attempt or session: resolution requires its identity to be current/official in the runtime. An orphan locator from failed preparation returns a conflict, not fake success. The same active accepted start operation replays its initial snapshot. Superseded sessions/running attempts return409 RETRY_SESSION_CHANGED; superseded completed result/review IDs return409 RESULT_CHANGED.

`topic_retry_archive` preserves immutable full attempt history keyed by attempt ID/revision before a running attempt is discarded or an old completed retry result is overwritten. Archive preparation may leave an orphan if CAS loses, but archive documents never drive business reads, status or official scores. No after-CAS projection is required for publication. Active runtime does not accumulate all historical sessions.

## HTTP contract

The source remains FE `docs/handout/topic-learning/api.openapi.yaml`, not this document. Added fields on RetrySessionDto are exactly itemsSnapshot and nextTopicItemId. There are no skippedItemIds/requiresReconciliation fields in this scope.

- GET `/topic-attempts/{attemptId}/review`: current completed attempt/content and every quiz with correct feedback/firstTryCorrect; never returns wrong submission history and never writes. Explicit access updates recency separately, leaving attempt revision/frontier/results unchanged.
- POST `/topic-items/{itemId}/attempts` with mode RETRY: create an ITEM session on the latest published version. Existing active session requires its exact observed replaceRetrySessionId; cancel in FE sends no request. Stale observed ID conflicts without replacing the new session.
- POST `/topics/{topicId}/retry-sessions`: create a TOPIC session, freeze all ordered visible item versions and immediately create its first CONTEXT/0 attempt.
- GET `/topic-retry-sessions/{retrySessionId}`: read only. currentAttemptId is null between items; nextTopicItemId identifies the first still-pending frozen item. It never creates an attempt.
- Explicit next start sends mode RETRY + retrySessionId. It uses the frozen version and does not re-check level. An existing current attempt on that exact item resumes; a mismatched item/session conflicts.
- Existing attempt/content/quiz/answer/advance/access routes resolve RETRY through the same runtime. AttemptDto.mode is RETRY, retrySessionId identifies the session, and each retry's revision/first-try history is independent from LEARN.
- Result retrySession/next fields belong to the completed attempt's own session. An old LEARN result does not acquire the currently running retry's frozen sequence. A completed retry from a superseded session has no active next retry action.

New retry creation checks level; continuation and review do not. ITEM requires a completed item; TOPIC requires a completed nonempty topic. Current result references/actions come from BE. Review and retry inputs remain feature-local, without infrastructure/auth/dependency changes.

## Idempotency key scope (accepted contract nit)

The cross-mode Idempotency-Key guard is intentionally not implemented in this delivery; it is not pending work inside Slice C.
The FE journal keys each mutation per `[learnerId, topicId, targetItemId, action, body]` (`engolearn-frontend/src/features/topic/services/topic-mutation-journal.ts:38`, `useTopicLaunch.ts:46`), so one key is never sent for two different actions or items; only a non-conforming client can reach the cross-namespace case.
Current BE behaviour, stated as observed: RETRY start records live in `topic_retry_locators` keyed by `(owner, itemId, key)`; LEARN start records live in `user_topic_item_attempt` keyed by `(userId, key)`. Reusing one key across those two namespaces, or across two items in RETRY, therefore returns success instead of `409 IDEMPOTENCY_KEY_REUSED`.
This is a known, accepted contract nit: it is not fixed and not covered by any test, and no change is claimed here.

## Verification

- First package: PASS,228 source files compiled. Historical mocked suites/full verify were not run per the user's focused-test constraint.
- ArchitectureTest: PASS9/9 before the final archive-only addition; final package result and parent live acceptance are reported at handoff completion.
- Parent reported one56-request real clone state scenario PASS before the archive addition: review read-only, TOPIC resume, old score while retry runs, lower-score replacement, explicit next-item start, observed-session replacement/stale-session conflict, and completed result preservation. No claim of a full-topic repeat, hide/unhide acceptance or network-fault injection.
- Closing build state check: `target/engolearn-0.0.1-SNAPSHOT.jar` LastWriteTime 2026-10-28 17:46:42 and 0 files under `src/` newer than it, so there is no source delta since the 17:46 package; no rebuild performed.
- Archive read, read-only, one document: attempt 88d00adcc47a216f4d355c55 in `engolearn_quiz_verify` has `_id` 8bd8a7a2f68e4c10e05a7c9c, version 7, status IN_PROGRESS, 1 quiz submission in the snapshot. Host `mongosh` is not installed; the read ran inside the existing `mongo:8.0.29-noble` container `mongodb_srv` with no writes.
