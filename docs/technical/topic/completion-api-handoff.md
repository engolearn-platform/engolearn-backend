# Topic completion and results

See [attempt/quiz](quiz-api-handoff.md) for answer/replay rules and [retry/reset](retry-api-handoff.md) for replacement results and generations. These pages describe current behavior; implementation and verification history belongs in Git.

## Atomic completion and scoring

For LEARN, `UserTopicItemAttempt` is the authority. The final correct answer writes the submission, revision, `COMPLETED`, null frontier, completion time, immutable `completion_result` counts/title and receipt in one version-checked document update. There is no later best-effort projection required to unlock the next item or expose the result. RETRY publishes through its own runtime CAS.

First-try correctness comes from the first stored submission for each quiz. Item accuracy rounds the ratio of correct-first-try count to quiz count; topic accuracy sums counts before rounding. Topic completion includes current visible items only; an empty topic is not completed. An unfinished retry preserves the current official score; completing it can replace that score with a lower one.

Catalog/detail/progress overlay embedded completed attempts with supported legacy progress references in memory. GET does not repair/migrate data. Missing historical result counts fail closed instead of inventing a score. All effective selection is restricted to the current topic generation.

## HTTP contract

Paths have prefix `/api/v1`; all operations are authenticated and owner-scoped. Domain errors use `ProblemDetail.code`.

| Method / path | Contract |
| --- | --- |
| POST `/topic-attempts/{attemptId}/completion` | `{revision}` plus `Idempotency-Key`; compatibility recovery for LEARN when every pinned quiz is already stored correct; returns attempt |
| GET `/topic-attempts/{attemptId}/result` | Current official completed item result with next-item/action metadata; retry metadata belongs to that attempt's own session |
| GET `/topics/{topicId}/result` | Published, nonempty topic with every visible item complete; aggregate result and reset availability |
| GET `/topic-attempts/{attemptId}/review` | Current completed content/quizzes with correct feedback and firstTryCorrect; no wrong-submission history or GET writes |

Recovery adds no submission, regrading or progress reset. An exact accepted key/body replays its saved response; changed fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`. New stale/already-completed/abandoned operations return `409 STATE_CHANGED`; unfinished quizzes return `409 ATTEMPT_NOT_COMPLETED`. RETRY completion is committed by the final answer, so the recovery mutation rejects it.

If an old attempt has its final correct answer stored but is still IN_PROGRESS, explicitly recover completion, then fetch attempt/result. Replaying the old answer repeatedly cannot change its recorded running response into a completed response.

## Visibility, replay and next item

Next-item selection and predecessor checks use the current visible outline. An explicit LEARN start can CAS-abandon an owned hidden running attempt with `abandoned_reason=ITEM_HIDDEN`, freeing its active slot without deleting history or fabricating completion. GET does not perform this reconciliation. Started history remains in personal catalog even if its items become hidden.

Owned unavailable items return `410 ITEM_HIDDEN`; missing/foreign attempts return `404 CONTENT_UNAVAILABLE`. Old generations return `409 TOPIC_RESET`; a superseded official result/review returns `409 RESULT_CHANGED`. Clients should reopen the current topic/result rather than restoring stale handles.

Receipt snapshots remain stable after completion. Old running receipts can retain their earlier revision/position/access time and null completedAt. Nullable `roundedProgress` preserves legacy floor-based receipt percentages; new receipts use rounded percentages. Final receipts replay the accepted completed revision, not a later access state. See [quiz replay rules](quiz-api-handoff.md#replay-and-errors).

Generation reset clears effective completion and active retry selection without deleting old stored attempts. Its request/replay rules and persistence boundary are owned by [retry/reset](retry-api-handoff.md).
