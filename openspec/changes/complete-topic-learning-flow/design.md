## Context

See [proposal](proposal.md) for scope. Discovery baseline: BE `4b36f15`, FE `a5d0b4a`; business decisions `aaff29c` (2026-10-27). These are observed code baselines, not evidence that the following design is implemented.

| Area | Observed today | Required work |
|---|---|---|
| Identity | DB local profile selected by TOPIC_LOCAL_LEARNER_ID; loopback only | Preserve; no auth work or fixed learner fallback |
| Catalog | Real personalized discover/mine/detail/progress | Completion/retry/hide actions and projection consistency |
| Content | start/read/content/advance/access, revision CAS, content pinned | Visibility enforcement; preserve purpose indexes and legacy progress |
| Quiz | Existing VOs only; FE client declares future endpoints | Current quiz, grading, feedback, persistence, final completion |
| Results/retry | No complete live implementation | Item/topic result, review, retry sessions |
| Storage | Mongo standalone; transaction manager opt-in | Atomic business commit without infrastructure change |

Relevant seams: TopicAttemptService currently owns start/content/advance/access; TopicLearnerCatalogService enriches permissions/progress; TopicAttemptContentMapper deliberately omits formal quizzes; QuizModuleVo holds questions without question IDs, QuizOptionVo has label/text/correctness; QuizProgressVo records submissions by quiz_id. UserTopicItemAttempt already has @Version, pinned version and mutation receipts. Existing LEARN attempts may have null parent and explicit attemptType; older attempts may identify type through a parent. Both must remain readable.

FE entry is TopicAttemptEntryView -> TopicLessonView/useTopicLesson, with TopicLessonNav and TopicLessonContent. Current canViewPosition accepts past quiz positions and must be restricted before enabling quiz navigation. Existing mock-era learning views must not be remounted.

## Goals / Non-Goals

Goals: complete real vertical slices with stable HTTP contracts, persisted correctness, explicit recovery, existing attempt compatibility and current-visibility enforcement. Keep feature-local services/controllers/DTOs and shared envelope/ProblemDetail conventions.

Non-goals: repo-wide refactor, new package root/dependency, auth, replica-set conversion, automatic DB reset, fake payload fallback, or running mocked suites as integration evidence. This proposal does not execute migrations or sample imports.

## Decisions

### 1. Contract ownership and concrete routes

The machine-readable baseline is FE `docs/handout/topic-learning/api.openapi.yaml` (JSON-compatible YAML), generated types and topic-api.service.ts. It describes a target, not current BE availability. Before implementation, update that file/types with the deltas below and commit the contract update; do not create a competing API_contract file. BE DTOs/services must match it. Root is `/api/v1`; successful responses use `{data,timestamp}`, errors ProblemDetail with stable `code`.

| Route | Input | Output / behavior |
|---|---|---|
| GET /topic-attempts/{id}/quiz | owned attempt | CurrentQuizDto `{attempt,quiz}`; quiz:null when COMPLETED; 409 before quiz |
| POST /topic-attempts/{id}/quizzes/{quizId}/answers | `{revision,optionId}`, Idempotency-Key | QuizAnswerDto `{quizId,isCorrect,feedback,attempt}`; wrong feedback:null; correct feedback belongs to submitted quiz even though attempt has advanced |
| GET /topic-attempts/{id}/result | committed current completed attempt | ItemResultDto with attemptId/topicId/topicItemId/title, firstTryCorrectCount,totalQuizCount,accuracyPercent,completedAt,nextTopicItemId,nextItemAvailability and retrySession per existing schema |
| GET /topic-attempts/{id}/review | current completed result | ItemReviewDto with pinned content, reviewed quizzes/feedback and first-try flags; no writes |
| GET /topics/{id}/result | all current visible items completed | TopicResultDto aggregated counts/items/retryAvailability; otherwise409 TOPIC_NOT_COMPLETED |
| POST /topic-items/{id}/attempts | existing StartAttemptRequestDto | LEARN unchanged; RETRY creates ITEM session or explicitly starts next item of a supplied TOPIC session |
| POST /topics/{id}/retry-sessions | `{replaceRetrySessionId}`, Idempotency-Key | RetrySessionDto; freezes release/items/versions and immediately starts first CONTEXT/0 |
| GET /topic-retry-sessions/{id} | owner | RetrySessionDto, read-only; next attempt is never created by GET |
| POST /topic-attempts/{id}/access | Idempotency-Key | Monotonic recency only, includes current completed review, no attempt revision increment |

DTO detail follows the named schemas already in OpenAPI. Answer request does not send stage/index, correctness or score; quizId resolves the current pinned question. Wrong answer increments revision and stores submission but does not move frontier. Correct answer increments revision and returns feedback containing correctOptionId/explanation/optional grammar/audio. No first-try metric in learner response until result/review. Busy FE disables selections until resolved.

Required contract deltas:
- Add stable ITEM_HIDDEN (410) for owner-visible hidden-item denial on attempt/content/quiz/answer/result/review/start/retry/access. Foreign/nonexistent attempt remains404 before disclosing visibility. Disabled actions use ITEM_HIDDEN reason where relevant.
- RetrySessionDto retains itemIds/currentAttemptId/completedItemIds and adds itemsSnapshot [{topicItemId,topicItemVersionId,orderIndex}], skippedItemIds:string[], nextTopicItemId:string|null and requiresReconciliation:boolean. itemIds/itemsSnapshot stay frozen; skippedItemIds is derived from current hidden membership, never added to completedItemIds. nextTopicItemId is the first eligible pending item or null. requiresReconciliation is true when the stored active state must be closed/advanced by explicit mutation. GET is read-only. No fake completed attempt for a skipped hidden item; an effectively ended session may report COMPLETED with requiresReconciliation:true while stored state is repaired only by mutation. Sync these exact fields in OpenAPI before consumers use them.
- Clarify that completed content is read through review; normal attempt content must not reopen completed/abandoned attempts. Final completion has position:null.
- Existing answer description saying final answer commits result atomically means one authoritative business commit, not a claim of multi-document Mongo transaction. Document the protocol in section 3.

Error table: 400 INVALID_REQUEST/invalid option; 401 learner required; 404 owned resource not found; 409 STATE_CHANGED (stale revision), IDEMPOTENCY_KEY_REUSED, RESULT_CHANGED, RETRY_CONFIRMATION_REQUIRED, RETRY_SESSION_CHANGED, TOPIC_NOT_COMPLETED; 410 ITEM_HIDDEN; 422 LEVEL_TOO_LOW/PREDECESSOR_INCOMPLETE/CONTENT_UNAVAILABLE; 503 only for genuinely unavailable persistence, never fake success. Re-read authoritative state on409; unresolved response/network errors retain the same mutation key.

### 2. Mapping actual content without silently rewriting it

Build an internal normalized quiz sequence from pinned TopicItemVersion: supported types in fixed order, module order_index then original module position/ID, questions in array order. quizId is a deterministic, version-scoped composite of module identity plus original question index (not only module quiz_id). Option IDs use original option positions, independent of A/B labels, text or display order. MINOR cannot reorder or change identity. Persist new IDs for newly authored content when possible; never regenerate a pinned attempt's ordering because a new release exists.

Map modern quiz_data/answer_data and legacy prompt/options/correct/sentence through one mapper. If both answer representations conflict, option mapping is ambiguous, no valid answer exists, or required content is missing, return CONTENT_UNAVAILABLE with a diagnostic for the maintainer; do not guess or invent choices/grammar. Learner DTOs whitelist public fields: never serialize is_correct, correct, answer_data, or explanation before correctness. Optional quickCheck remains local with its answer, separate from formal quiz policy.

Valid new published sample content must have 3–5 questions per supported type. Audit the real existing incomplete content; do not silently relax publish gates or patch a version already referenced by attempts. Unsupported malformed content is a blocker for that item; the separate data pack supplies a valid, independent topic.

EXPRESSIONS preserves current flattening: groups in stored array order, purposes sorted by orderIndex then purposeId, all examples of one purpose on one screen. Keep IDs and 0-based position semantics already used; no DB group removal. Nav lists purposes directly under a collapsible Mẫu câu section.

### 3. Standalone-safe authoritative commit, not disconnected repository saves

Choose a per-user/topic runtime aggregate with single-document CAS, rather than enabling Mongo transactions or accepting eventually inconsistent official results. Add a topic-owned `topic_learner_runtime` collection, unique `(user_id,topic_id)`, with revision, active LEARN state, active RETRY session/generation and child state, current result references by item, recency and compact replay receipts. This is a proposed additive collection, not an existing implementation. Existing item/topic attempt and progress documents become compatible history/projections, not competing write authorities for migrated topics.

Move mutations for a migrated user/topic through the same aggregate revision: start/resume, content advance, quiz answers, retry replacement and completion. The single CAS must validate the active attempt/session generation, persist the accepted selection/first-try state, move frontier and, on final correctness, switch current-result reference and retry cursor together. Externally exposed attempt revisions keep existing per-attempt semantics; unrelated access events may retry aggregate CAS internally without pretending learning revision changed. Same generation check prevents a replaced retry from committing a late answer.

Large immutable completed-result payloads may be prepared in a separate result document, deterministically identified by attempt+completion operation. It contains the final quiz results and first-try counts. Preparation is not publication. The aggregate CAS publishes its reference only after the result exists durably. One committed result reference is the authoritative boundary for attempt COMPLETED, result/review, catalog permissions and next-item unlock. A final answer accepted as complete must never depend on a subsequent best-effort projection update.

| Interruption | Authoritative behavior |
|---|---|
| Before result preparation | No completion; retry same operation |
| After preparation, before aggregate CAS | Prepared result invisible; retry verifies fingerprint and attempts same CAS |
| After CAS, before HTTP response | Receipt returns original accepted response; GET resolves committed result |
| After CAS, before legacy projection writes | All reads use aggregate+committed result; explicit mutation/startup repair materializes projections idempotently |
| Concurrent answer/retry replacement | Only one aggregate revision wins; loser reloads; no abandoned generation commits afterward |

Persist receipt/fingerprint with CAS before checking a replay's old revision; same key/different request conflicts. Scope keys consistently per learner across operation/resource so identical keys cannot silently mean different actions. Preserve replayed response snapshots, not responses rebuilt from a later attempt frontier. Do not return a fabricated success for an uncommitted prepared result.

Initial migration is lazy on an explicit mutation, not GET: load existing owner topic/item attempts/results, preserve IDs/pinned versions/positions/submission history and completed references, insert aggregate with a unique index, reread on race. GET for an unmigrated topic uses the existing read adapter; once aggregate exists all learner reads prefer it. Normalize old/null parent vs standalone LEARN without creating duplicate sessions. If historical state is contradictory, report it and stop migration for that topic; no reset. Persist repair work; GET must not perform hidden repairs. Audit hidden stale LEARN attempts so they cannot hold the old partial unique index hostage when the next visible item starts.

Retain current schema VOs as adapters/projections; do not mechanically remodel every collection to match illustrative schema JSON. Keep active runtime bounded to current states and compact result refs; move completed history to immutable records. Monitor BSON document size and avoid duplicating media/full snapshots in runtime. Do not silently discard accepted wrong-answer history or receipts to fit a document.

Rejected alternatives: multiple independent save() calls (partial completion), @Transactional on standalone (unsupported), infrastructure conversion (not authorized), or advancing only after FE Continue (contradicts saved correctness).

### 4. Visibility, version and retry lifecycle

Resolve current visibility from the admin/publish source already used for catalog, not from pinned item version alone. On every owned read and mutation check visibility at request handling; a committed hide denies subsequent requests even for a running/pinned attempt. No extra websocket/poll loop. FE clears answer feedback/audio when the next request reports ITEM_HIDDEN, explains and navigates to detail, then refetches actions/progress/mine. Cached pixels cannot be remotely erased without a request; do not claim otherwise.

Keep hidden history. In a TOPIC retry, keep itemsSnapshot immutable but filter current visibility when deriving remaining sequence. Reconcile a hidden active item on the next explicit mutation, release its active slot, and never manufacture a completed result. If no eligible pending item remains, close session on that mutation; GET may expose an effectively ended session/nextItem:null without writing. ITEM retry hidden target becomes unavailable and is closed by mutation reconciliation; if unhidden later, a new retry may be requested, not automatically created. Ordinary hidden LEARN preserves its historical attempt but cannot block starting the next visible item; mark it ABANDONED with hidden reason only in an explicit reconciliation, not by GET. Existing completed results remain stored and are included again if the item becomes visible.

Check level only for new LEARN/retry session creation. A TOPIC session continuing its frozen next item is continuation of that session, not a new level gate. A fresh retry pins latest published version; existing session keeps its frozen versions. Replacement compares observed session ID/generation; cancelling writes nothing. New item publication can change topic status independently of an active retry.

MINOR modifies allowed text/audio in place without changing sequence or answer identity; historical score remains the recorded first-try outcome. MAJOR changes quiz set/answer meaning and requires a new version. Do not add an audit/version rewrite just to recreate pre-MINOR wording.

### 5. FE behavior and handoff

Use the existing attempt shell with header progress and native/collapsible part dropdowns; no extra top-left/top-right legacy progress cards. Fetch current quiz only at quiz frontier. Feedback is transient UI state tied to the answered quiz; a correct response already contains the next authoritative attempt state. Continue dismisses feedback and reads the next quiz; final Continue goes to result. Reload after success goes directly to next question/result. Do not use content advance for quiz stages or allow URL/nav selection of solved quizzes in running attempts.

Use shared content/quiz presentational components in review with disabled inputs and review data. Review can select any completed quiz; running mode cannot. Result shows completion, accuracy fraction/% and legal actions; item result offers next item, topic detail, review/retry; topic result lists per-item accuracy and retry. No XP, false timers, skip, invented media, or per-quiz retry.

Client/server state: useFetch + abort for reads, existing mutation journal for uncertain writes, one in-flight selection, clear cached viewer-specific state on profile change. Revalidate detail/progress/mine after completion/retry/hide and invalidate stale result references. Follow FE tokens/components and accessibility loading/error/keyboard behavior. See FE plan for explicit file touchpoints and per-slice acceptance.

## Risks / Trade-offs

- Aggregate migration affects existing attempts -> explicit-mutation migration, compatibility reader, no reset and snapshot-preservation check using the current attempt.
- Standalone consistency needs more application logic -> implement aggregate/recovery before claiming completion; targeted interruption check at commit boundaries, no repeated broad suites.
- Existing DB has incomplete content -> use separate valid data pack; report malformed originals without patching pinned content.
- Admin visibility source may be represented differently by release vs item status -> use the same effective source as admin/catalog; one visibility resolver shared by learner operations, verify against an actual hide.
- Old API target lacks hidden-session recovery fields -> sync OpenAPI/types first; do not invent ad-hoc client fields.
- Unbounded submissions/receipts -> avoid full-content duplication, retain audit/immutable records; storage exhaustion is an operational error, never a wrong answer or fake completion.

## Migration Plan

1. Commit docs/contract changes; create isolated sample script separately. Do not import sample data automatically.
2. Implement compatibility reader and runtime aggregate with stable IDs/indexes; preserve old attempt IDs/revisions. Verify one existing attempt resumes unchanged before quiz work.
3. Deliver quiz, completion, review/retry in task slices. Each slice must show a real FE proxy -> BE -> DB path; a missing endpoint is a blocker to report, not an invitation for fixtures.
4. Keep old item-content/position routes 410. Update live evidence only for observed flows; no archive until implementation/verification actually finish.
5. Rollback application changes only before new runtime mutations are enabled. After live migration, old writers cannot safely be re-enabled against projection data: disable learning writes and forward-fix/reconcile from aggregate; no automatic DB downgrade or deletion. Any destructive restoration needs a separate explicit decision.

## Amendment A — Slice A implementation deviations (2026-10-28)

Amendment A supersedes only the lines named below, and only for Slice A (quiz slice). The original text stays in place as the target for later slices: nothing in Decisions 1–5 or the Migration Plan is withdrawn for Slice B/C.

### A1 Storage

`topic_learner_runtime` (§3, line 64) and Migration Plan step 2 were not built in Slice A. Quiz answers commit as one single-document CAS on `UserTopicItemAttempt` (`@Version` + `mutation_receipts`). Trade-off, stated plainly: there is no cross-document atomicity, so a runtime aggregate is still required before Slice B result/publication work, and §3 stays the target for that. The deviation was approved by the user (handoff §9 decision 1).

### A2 Routes implemented

Only the two quiz routes exist: GET `/topic-attempts/{id}/quiz` and POST `/topic-attempts/{id}/quizzes/{quizId}/answers`. Effective behavior: GET returns `quiz:null` when no quiz applies and 409 before the quiz frontier; POST returns feedback only when correct, a wrong answer stays retryable and is persisted, and a correct answer advances the frontier in the same write. Two behaviors differ from §1/§5:

- replay is answered from the stored receipt before the 404/409/410 checks, so a same-key replay returns the committed response even after the frontier moved or the item was hidden;
- a correct final answer leaves the attempt `IN_PROGRESS` with no result until Slice B.

Reference handoff §3 (shapes), §4 (check order), §9 (decisions).

### A3 Visibility

The implemented predicate is the five-condition AND recorded in `.artifacts/complete-topic-learning-quiz-slice/visibility-ruling.md` §1: pinned version not `HIDDEN` (V1), topic exists (V2), `latestPublishedVersion` present (V3), that release exists and is `PUBLISHED` (V4), and the release still contains the attempt's `topicItemId` with `status != HIDDEN` (V5). Check order: ownership/version 404 first, then hidden 410, then frontier 409 (full POST order in handoff §4). This supersedes the looser §4 statement; handoff §4 is the effective contract.

### A4 Identity and mapping

- `quizId = <topicItemVersionId>:quiz:<moduleKey>:<questionIndex>`
- `optionId = <quizId>:option:<originalPosition>`
- `moduleKey` = `orderIndex` when present, else the module's array position
- `index` and `totalInType` are counted per quiz type across the whole item, not per module
- unsupported quiz types are skipped
- FILL_BLANK requires 4–6 options; SITUATIONAL_CHOICE exactly 4 with exactly one correct option
- missing non-nullable field -> `CONTENT_UNAVAILABLE` 404; nullable field -> null

This refines §2. §2's 3–5 questions-per-type line is a publish-gate rule of the data pack, not a mapper rule; the mapper does not enforce it.

### A5 Deferred to Slice B

- last correct answer without result: `IN_PROGRESS`, `quiz:null`, no result (handoff §9 decision 2);
- `completedQuizCount` one lower than actual until completion exists (slice artifacts; same missing result as above);
- the partial unique index `one_running_learning_item_per_topic` still held by a hidden in-progress attempt;
- hidden-item-mid-progress reconciliation — explicitly deferred by the user (handoff §10).

### A6 Known deltas

- `410 ITEM_HIDDEN` is missing from the FE contract — handoff §7 item 1;
- a missing `Idempotency-Key` returns the framework's default 400 body without a `code` field — handoff §6.6 note / §7 item 6.

Data blockers are recorded in handoff §12; Slice A fail-closes with 404 `CONTENT_UNAVAILABLE` rather than relaxing a rule.
