# Topic attempts and quiz

This page owns the attempt/quiz behavior needed to maintain Topic. See [completion](completion-api-handoff.md) and [retry/reset](retry-api-handoff.md) for the rest of the lifecycle. Detailed consumer schemas live in the [frontend OpenAPI](../../../../engolearn-frontend/docs/handout/topic-learning/api.openapi.yaml); check actual [controllers](../../../src/main/java/fit/iuh/engolearn/topic/controller), DTOs and services before changing the contract. Older schema examples may describe superseded behavior.

Topic learning uses authenticated account identity and persisted CEFR level through `user.contract.UserLevelLookup`; it does not use local-profile selection or a default learner. Only catalog/detail GETs are public; learner operations require USER, CONTENT_MANAGER or ADMIN. Missing profiles fail explicitly. See [authentication and profile setup](../../runbooks/user-auth.md).

## Attempt lifecycle and HTTP

Paths below have prefix `/api/v1`. Success uses `ApiResponse` (`data`, `timestamp`); domain failures use `ProblemDetail.code`. Mutations require a nonblank `Idempotency-Key` of at most 128 characters. Clients do not submit correctness, score or completion flags.

| Method / path | Behavior |
| --- | --- |
| POST `/topic-items/{itemId}/attempts` | Start/resume `LEARN` on a pinned published version; `RETRY` follows the [retry contract](retry-api-handoff.md) |
| GET `/topic-attempts/{attemptId}` | Owned attempt, saved frontier and revision |
| GET `/topic-attempts/{attemptId}/content` | Pinned content for an in-progress attempt; completed content uses review |
| POST `/topic-attempts/{attemptId}/advance` | `{revision,stage,itemIndex}` advances only the saved current content unit |
| POST `/topic-attempts/{attemptId}/access` | Record recency without advancing frontier or attempt revision |
| GET `/topic-attempts/{attemptId}/quiz` | `{attempt,quiz}`; current unanswered quiz, or null after completion/when none remains |
| GET `/topic-attempts/{attemptId}/quiz-navigation` | Read-only `{attempt,entries}` for an in-progress attempt |
| POST `/topic-attempts/{attemptId}/quizzes/{quizId}/answers` | `{revision,optionId}`; returns `{quizId,isCorrect,feedback,attempt}` |

Learning proceeds through `CONTEXT`, `VOCAB`, `EXPRESSIONS`, `FILL_BLANK`, then `SITUATIONAL_CHOICE`, skipping absent content through the existing mapper/position rules. Reads never advance, grade, complete or update recency. LEARN has one running item per user/topic/current generation. A new attempt pins its version; republishing does not silently replace that snapshot.

Quiz GET/answer requires the quiz frontier. Only the current unanswered quiz accepts a new answer. Wrong answers are recorded and increment revision without advancing; feedback is null. Correct answers return feedback and move to the next unanswered quiz. The last correct answer commits completion as described in [completion](completion-api-handoff.md).

Navigation entries are `LOCKED`, `CURRENT` or `COMPLETED`. Before reaching exercises all are locked. Locked entries expose only identity/type/index/status. Current entries expose quiz without feedback/firstTryCorrect, including after a wrong answer. Completed entries expose reached quiz, correct feedback and first-submission correctness. Navigation does not change the frontier; completed attempts use review instead.

## Stable identity and content constraints

The [quiz mapper](../../../src/main/java/fit/iuh/engolearn/topic/mapper/TopicQuizMapper.java) orders types as FILL_BLANK then SITUATIONAL_CHOICE. Within each type it orders modules by `order_index` (array position when null, stable original order on ties), then question array order. `index` is zero-based across the whole type; `totalInType` is its total across modules.

- `quizId = <versionId>:quiz:<moduleOrderIndex-or-arrayIndex>:<questionIndex>`; duplicate derived IDs fail closed.
- `optionId = <quizId>:option:<originalOptionPosition>`; labels/text/display order are not identity.
- FILL_BLANK requires 4..6 nonblank options and exactly one correct option. Its correct text must occur exactly once in `sentence.text_en`; the mapper splits there into `sentenceBefore`/`sentenceAfter`.
- SITUATIONAL_CHOICE requires four options, one correct option and a nonblank prompt mapped to `questionVi`.
- Module title and question explanation must be present. `situationVi` comes from `quiz_data.scenario_vi`; a same-named field elsewhere is not read. Missing optional scenario/media remains null; `imageUrl` and feedback `grammarNoteVi` are null.
- Feedback exposes correct option ID, explanation and available sentence translation/audio; SITUATIONAL_CHOICE has no completed sentence. Incorrect/unreached exercises do not expose answer keys or explanations.

Malformed content returns `404 CONTENT_UNAVAILABLE` rather than fabricated questions or relaxed validation. Historical data scans are not current DB status; inspect affected pinned versions when diagnosing failures. Import tool usage belongs in [its README](../../../tools/mongo-json-importer/README.md).

## Replay and errors

LEARN answer/advance/completion writes use version-checked single-document updates with embedded mutation receipts. Same accepted key/payload replays the saved response before revision validation; changed payload returns `409 IDEMPOTENCY_KEY_REUSED`. A replay can contain an older revision/frontier: fetch the authoritative attempt when current state is needed. LEARN start replay retains its original start projection; it is not a fresh progress read. Key scope limitations across LEARN/RETRY starts are in [retry](retry-api-handoff.md#idempotency-scope).

For LEARN answer/completion, an accepted receipt can replay before current visibility checks, without grading or writing again. Ownership and generation checks still apply; reset invalidates old owned handles. RETRY also validates its current runtime/visibility before processing a mutation; do not generalize the LEARN replay ordering to every route.

| Status / code | Meaning |
| --- | --- |
| 400 `INVALID_REQUEST` | Invalid request/key or option outside the addressed current quiz |
| 404 `CONTENT_UNAVAILABLE` | Missing/foreign attempt, missing pinned content or malformed quiz |
| 409 `STATE_CHANGED` | Stale revision, wrong frontier/current quiz or incompatible attempt state |
| 409 `IDEMPOTENCY_KEY_REUSED` | Accepted key reused with a different fingerprint |
| 409 `TOPIC_RESET` | Owned handle belongs to an old topic generation |
| 410 `ITEM_HIDDEN` | Owned item/release no longer available |

Authentication/role failures are handled by security; callers must not depend on `ProblemDetail.title` prose. The Topic request handler currently sets title to its code. Validation/security exceptions can have different shapes; the table is not a promise that every framework error carries the same code.
