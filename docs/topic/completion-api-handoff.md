# Topic completion — Slice B handoff

## Current generation/reset contract (2026-10-29)

Official completion remains the normal final-answer write or retry runtime CAS. A retry preserves the current official result until completion and may replace it with a lower score. There is no item restart/replacement operation.

Topic detail adds `generation` and `resetAvailability`; topic result adds `resetAvailability`. The confirmed topic reset is a generation increment, including during an active retry. Catalog completion overlays, progress totals, recency and result/review selection use only the captured current generation. Old owned attempt/result/review references return `409 TOPIC_RESET`; they cannot restore completion after reset. Old data remains internal and is not a history API. See retry handoff for reset request/replay semantics.

Date: 2026-10-28. BE branch: `feat/complete-topic-learning-flow`.
Scope: LEARN final correctness, item/topic result, compatibility recovery, current progress/actions, and visible next-item selection. Review/retry/session behavior remains Slice C. Machine-readable contract is the sibling FE `docs/handout/topic-learning/api.openapi.yaml`; this document does not create a competing schema.

## Authoritative commit and compatibility

`UserTopicItemAttempt` remains the single-document authority for B. The final correct answer CAS writes the accepted submission, revision, `COMPLETED`, null frontier, completedAt, embedded immutable `completion_result` counts/title, and mutation receipt together. There is no later best-effort projection write required to unlock the next item or expose the result. This is Design Amendment B; no replica-set conversion or runtime aggregate is added here.

Catalog/detail/progress resolve embedded completed attempts alongside legacy `UserTopicItemProgress` references. The compatibility overlay is in memory only: GET does not migrate or repair documents. A legacy completed reference remains readable from its stored counts; missing historical counts fail closed rather than inventing a score. LEARN has one active item slot; C must define topic/session publication before supporting competing retry generations.

The original first-try submission history is authoritative. The snapshot counts a quiz as first-try correct only if its first stored submission is correct. Accuracy is rounded after dividing counts; topic accuracy sums counts before rounding. Current topic completion includes visible items only, and an empty topic is not completed.

## Routes

All routes are under `/api/v1`; success is `{data,timestamp}`, errors retain stable ProblemDetail `code`.

| Operation | Request | Response |
|---|---|---|
| Final `POST /topic-attempts/{attemptId}/quizzes/{quizId}/answers` | Existing `{revision,optionId}` + Idempotency-Key | Existing QuizAnswerDto; final correctness returns completed AttemptDto, null position, all steps/quizzes done and feedback for the answered quiz. |
| `POST /topic-attempts/{attemptId}/completion` | `{revision}` + Idempotency-Key | AttemptDto. Compatibility mutation only: every pinned quiz must already be persisted correct. No submission is added. |
| `GET /topic-attempts/{attemptId}/result` | Owned completed attempt | Existing ItemResultDto, including `nextTopicItemId`, `nextItemAvailability`, and null retrySession. |
| `GET /topics/{topicId}/result` | Published topic with every visible item complete | Existing TopicResultDto; item actions/retry availability reflect actual implemented capabilities. |

Completion recovery checks ownership before disclosing visibility. Matching accepted recovery key/body replays before revision validation; a reused key with a different fingerprint is 409 IDEMPOTENCY_KEY_REUSED. A new operation with stale revision, abandoned state, or an already-completed attempt is 409 STATE_CHANGED. An unanswered quiz is 409 ATTEMPT_NOT_COMPLETED. Hidden owned items are 410 ITEM_HIDDEN; foreign/nonexistent attempts are 404 CONTENT_UNAVAILABLE. Recovery never resets progress, regrades historical submissions, or writes on GET.

For the existing Slice A attempt whose last answer is already stored but status is IN_PROGRESS, FE offers explicit recovery. After confirmed completion it reads the authoritative attempt/result and clears the old pending answer. It must not repeatedly replay the old answer expecting its stored running response to change.

## Replay, visibility, and navigation

Receipt snapshots remain stable after completion: old running receipts still return their recorded position/revision/access time with completedAt null. A nullable `roundedProgress` receipt flag preserves old floor-based percentages, while new receipts use the contract's rounded percentage. New final receipts have a null stage and replay the accepted completed revision rather than a later access state.

Owned attempt/content/quiz/access/advance/result reads and new mutations enforce current availability. A completed content request is 409 STATE_CHANGED because content review belongs to C. Existing accepted answer/recovery receipts replay without a new write; the replay-vs-hidden exception remains as documented for Slice A.

Next-item selection and predecessor checks use the current visible outline. On an explicit LEARN start, an owned hidden IN_PROGRESS attempt is CAS-abandoned with `abandoned_reason=ITEM_HIDDEN`, releasing its active unique slot without fake completion or deleting history. GET never performs this reconciliation. Started history remains in personal catalog even if all associated items are later hidden.

## Verification ownership and limits

- BE agent: clean Maven package compiled 219 source files successfully; final incremental package is rerun after the legacy receipt flag change. Tests were skipped for packaging, not reported as behavioral verification.
- BE agent: `ArchitectureTest` passed 9/9, with all 34 existing test sources compiling. This structural check uses no mocked application behavior.
- Parent agent owns real HTTP/DB verification and application startup. It reported one genuine clone flow through five items and 30 quiz answers, including wrong/reload/replay and normal final correctness, before the receipt percentage compatibility fix. Exact evidence and final targeted replay/recovery/race results belong in the parent integration handoff; do not infer additional checks from this implementation description.
- Full `mvnw verify` and historical mocked suites were not run for B, per the user instruction forbidding mocked verification. The previously recorded baseline failure remains historical, not a new B test result.
- No DB reset, sample import, infrastructure conversion, review/retry implementation, push, or OpenSpec archive was performed by the BE agent.
