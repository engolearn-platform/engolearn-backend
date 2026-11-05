# Topic review, retry and reset

See [attempt/quiz](quiz-api-handoff.md) and [completion/results](completion-api-handoff.md) for shared behavior. Detailed response schemas live in the [frontend OpenAPI](../../../../engolearn-frontend/docs/handout/topic-learning/api.openapi.yaml); current controller/service behavior takes precedence over superseded examples.

## Current retry behavior

ITEM retry starts on the latest published version of a completed item and checks persisted learner level. An unfinished retry resumes the same item even with a fresh key. Another item in that topic returns `409 ITEM_RETRY_IN_PROGRESS`; any `replaceRetrySessionId` returns `409 RETRY_RESTART_NOT_ALLOWED`. A mismatched session returns `409 RETRY_SESSION_CHANGED`. Other topics have separate runtimes.

Catalog actions expose resume or new retry exclusively; `restartRetry` remains in the wire shape but is false. The official result remains readable until the retry completes. The final correct answer atomically publishes its replacement result, even when the new score is lower. Continuation/review does not recheck level.

Paths have prefix `/api/v1` and require authenticated ownership. Mutation keys are nonblank and at most 128 characters.

| Method / path | Behavior |
| --- | --- |
| POST `/topic-items/{itemId}/attempts` | `mode=RETRY`; start/resume ITEM retry; `retrySessionId` identifies explicit continuation |
| GET `/topic-retry-sessions/{retrySessionId}` | Read-only session; `itemsSnapshot`, currentAttemptId and nextTopicItemId; creates no attempt |
| POST `/topics/{topicId}/retry-sessions` | New TOPIC sessions are retired (`410 TOPIC_RETRY_RETIRED`); exact current legacy start replays remain supported |
| POST `/topics/{topicId}/reset` | `{generation: integer >= 0}` plus key; requires a completed visible nonempty topic and sufficient level; returns `{topicId,generation}` |

Existing legacy TOPIC sessions support GET and explicit next-item continuation using their frozen versions. Between items currentAttemptId is null; nextTopicItemId identifies the pending item, and only an explicit matching start creates it. A mismatched item/session conflicts. An old LEARN result does not acquire the current retry's session metadata. Do not offer new whole-topic retries as an alternative to reset.

## Persistence and publication

`topic_retry_runtime` holds one current-generation runtime per user/topic: frozen session, running attempt, completed item IDs and official completed retry results. Answer/advance/access/next-start mutate it through version-checked CAS. Completion writes accepted answer, result, cursor and receipt together. LEARN remains in `user_topic_item_attempts`; result selection overlays only official completed retries.

`topic_retry_locators` stores immutable identity/start metadata prepared before runtime CAS. A locator alone does not publish a session or attempt: it must resolve in the current runtime. Orphan preparation returns conflict rather than fake success. Accepted start replay preserves the frozen manifest while returning current session status/attempt/next-item state. Superseded session/running handles return `RETRY_SESSION_CHANGED`; superseded completed result/review handles return `RESULT_CHANGED`.

`topic_retry_archive` stores immutable attempt history before a running attempt is discarded or an old completed retry result is overwritten. Failed CAS can leave an orphan snapshot, but archive records never drive current progress or official scores. This is application storage, not a documentation archive.

## Generation reset

Reset increments generation and appends its receipt atomically in `topic_learner_state`; it creates no attempt/session and can invalidate an active retry. Exact key/body replay returns its saved result before eligibility checks, preserving newer work. Changed body returns `409 IDEMPOTENCY_KEY_REUSED`; stale observed generation returns `409 STATE_CHANGED`.

Legacy documents have generation 0. Current generation controls completion overlays, recency, progress and result/action selection. Old data remains stored internally and is not a public history API. Old owned attempt/session/result/review/mutation/replay handles return `409 TOPIC_RESET`; foreign handles remain `404 CONTENT_UNAVAILABLE`. Writes retain captured generation and cannot restore old effective progress after reset. The next ordinary operation starts LEARN in the new generation.

The declared unique running-LEARN index includes user, topic, attempt type and generation. Generation-aware identities and the running slot isolate new progress without purging old documents. Existing databases must have a compatible index; historical rollout notes do not establish automatic index/data repair in the current source. This single-document reset/CAS design does not remove the separate Mongo transaction requirement for user registration.

## Idempotency scope

There is no global key guard shared by LEARN and RETRY starts. LEARN start identity uses user/key (and topic/generation for later generations); RETRY start identity uses user/item/key/generation. Reusing a key across namespaces or distinct retry items is not guaranteed to return `IDEMPOTENCY_KEY_REUSED`; current active-item/session guards still apply. Do not reuse a key for unrelated actions/items. Mutation fingerprints reject changed payloads within their accepted receipt scope; reset has its own receipts.

Hide/unhide during retry has no automatic skip/reconciliation workflow; existing visibility denials remain. Retained legacy paths do not imply a new session-replacement or history API.
