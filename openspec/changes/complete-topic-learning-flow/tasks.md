## Status — Slice B coordination (2026-10-28)

| Task | Status |
|---|---|
| 1.1 | partial |
| 1.2 | partial (quiz 410 done; retry recovery deferred to C) |
| 1.3 | B authority implemented per Amendment B; C aggregate/session design open |
| 1.4a | done |
| 1.4b | not started |
| 1.5 | not started |
| 2.1a | done |
| 2.1b | done |
| 2.1c | not started |
| 2.2 | done |
| 2.3 | partial (FE implemented; acceptance incomplete) |
| 2.4 | partial (FE implemented; solved-quiz URL acceptance open) |
| 2.5 | partial (learner exercised quizzes; final completion pending B) |
| 3.1 | done (single authoritative item CAS, Amendment B) |
| 3.2 | done |
| 3.3 | done |
| 3.4 | done; network fault injection remains untested |
| 4.1 | not started |
| 4.2 | not started |
| 4.3 | not started |
| 4.4 | not started |
| 4.5 | not started |
| 4.6 | not started |
| 5.1 | partial |
| 5.2 | partial (FE build/lint/type drift passed; quiz UX acceptance open) |
| 5.3 | partial |
| 5.4 | partial |

## 1. Contract and compatible persistence foundation

Latest B evidence supersedes historical missing-endpoint notes below: BE implementation `4b9cfea`, FE completion contract `ecd5be3` plus corrected200 example removal, and sibling FE `docs/features/topic/learner/13-slice-b-live-verification.md`. Main attempt698a351bd05b76d98012c535 is now COMPLETED/revision19, result3/6=50%, original12 submissions preserved. FE3000/BE8080 remain the main runtime. Temporary clone8099/FE3001 supplied full-topic and browser acceptance. No C work or full-repository green verification is claimed.

- [ ] 1.1 BE/FE: read proposal/specs/design, current live-integration report and FE plan; record actual running BE/proxy URLs and route availability once. Verify viewer is the selected DB profile and report missing endpoints instead of starting a mock preview. FE3000 -> BE8080 with the selected DB learner was verified by the FE agent. Quiz routes were initially 404 and subsequently became available; the user exercised both types. Final observed GET quiz returns 200 with quiz:null/IN_PROGRESS, while GET result is 404. Complete acceptance evidence remains open; historical FE evidence is in the sibling `docs/features/topic/learner/09-live-integration.md` and `11-quiz-slice-a.md`.
- [ ] 1.2 FE: sync OpenAPI hidden errors and explicit retry recovery fields defined in design, regenerate topic-api.types.ts, adapt client signatures only if needed. Quiz GET/POST 410 is already declared in FE commit `82d2201`; generator and topic:types:check passed (51 schemas, DTO shapes unchanged). Full hidden coverage and retry recovery fields remain deferred to their consumer scope, with retry recovery in C. Do not mark the whole task done or repeat the completed quiz delta.
- [ ] 1.3 BE: implement topic-owned runtime aggregate/result storage with unique owner/topic index and compatibility reader; verify a legacy standalone LEARN and existing completed reference retain IDs, pinned version, revision and position without DB reset. Deliberately NOT built in Slice A; quiz mutations use single-document CAS on `UserTopicItemAttempt` instead — see `design.md` Amendment A1. Remaining scope moves with Slice B.
- [x] 1.4a BE: quiz answer mutations go through attempt CAS with request fingerprint/replay receipts. Evidence: handoff §9 decision 1 (CAS + receipts), §11 (replay adds no submission), §4/§6.6 (stale revision 409).
- [ ] 1.4b BE: route explicit start/advance/access/retry mutations through aggregate CAS with request fingerprint/replay receipts, migrating on mutation only. Not migrated: start/content/advance/access still use the existing per-attempt path. Verify GET is read-only, replay does not advance twice and stale revision does not mutate.
- [ ] 1.5 Data author: deliver the isolated sample pack specified in docs/topic/sample-data-plan.md; verify dry-run and rerun behavior against a separately selected local DB. Do not import into the user's DB automatically; hand over the command and manifest. Sample pack not delivered; user-owned sample files untouched.

## 2. Slice A — current quiz and persisted answers

- [x] 2.1a BE: normalize pinned module/questions/options with stable IDs and redact correctness/explanation from the current-quiz response. Verified on one real FILL_BLANK response with no answer/explanation leak (handoff §3.1, §11).
- [x] 2.1b BE: real clone HTTP run exercised SITUATIONAL_CHOICE and FILL_BLANK, checked current-quiz response shape and forbidden answer/explanation keys. Both types passed wrong/reload/correct and replay. Fresh clone browser also exercised the final SITUATIONAL_CHOICE. Historical initial BE evidence only covered FILL_BLANK; see the later B live report.
- [ ] 2.1c BE: redact correctness/explanation from the content response and reject ambiguous legacy answers (`answer_data` / `correct`). Not implemented or unverified: content redaction was never exercised against real persistence, and `TopicQuizMapper` reads only `is_correct`, so legacy ambiguity is neither detected nor rejected. Open decision for Slice B, not a verified behaviour.
- [x] 2.2 BE: implement current-quiz and answer routes with revision/current-question validation, persisted first-try failure and atomic frontier/receipt update. Verify wrong -> reload -> correct retains first-try failure; same-key replay adds no submission; stale different-key request returns409. Done — evidence: wrong -> reload -> correct keeps the first-try failure through `TopicQuizAnswerTest.wrongThenCorrectAppendsSecondSubmissionWithoutTouchingTheLoadedAttempt`; same-key replay adds no submission (handoff §11); stale revision returns 409 (handoff §6.6 real table); hidden item returns 410 and adds no submission (handoff §6.6, §11); pre-frontier requests return 409 (handoff §4, §11 smoke).
- [ ] 2.3 FE: add quiz state and two quiz renderers to TopicLessonView/useTopicLesson; use existing mutation journal and busy selection lock. Implemented in FE `b841354`: both renderers, exact pending request/key across reload, busy lock and transient feedback. User reports quizzes are playable; recorded acceptance for wrong/reload/correct, uncertain-response replay and audio remains incomplete. Final-answer contract mismatch currently retains a pending request; distinguish this from a network error as part of B.
- [ ] 2.4 FE: enforce running-quiz frontier in topic-lesson helpers/nav/URL, flatten EXPRESSIONS purposes without group UI, keep collapsible part navigation and header progress. Implemented in FE `b841354`. Real browser content-review/return preserved revision6/FILL_BLANK0/50%; helpers rejected future quiz positions on that real snapshot. A solved-quiz history URL still needs live acceptance; implementation alone does not close this task.
- [ ] 2.5 Integration: demonstrate real FE proxy -> BE -> DB for both types, reload after wrong and after correct-before-Continue. BE clone HTTP evidence covers FILL_BLANK; user subsequently exercised quizzes through FE. Do not treat this as complete recorded acceptance for both types/recovery. Complete the remaining A checks with B integration; final answer -> result remains a B check, not a temporary FE-generated summary.

## 3. Slice B — final correctness and completion

- [x] 3.1 BE: per Design Amendment B, final accepted answer, immutable embedded result counts, completed state/null frontier/time and receipt publish in one item-attempt CAS. Catalog/result do not depend on a projection write. No runtime aggregate or prepare/publish split is introduced in B; session publication stays C. Genuine HTTP final-answer replay and explicit recovery replay passed; concurrent quiz mutations returned200/409 with a single revision advance. Network/process-crash fault injection was not performed.
  Compatibility acceptance: preserve existing Slice A attempts whose final correct submission is already stored but whose status is still IN_PROGRESS (observed attempt `698a351bd05b76d98012c535`, revision18, quiz:null). Recover through an explicit, idempotent mutation using stored answers; do not reset progress, demand another answer, invent scores, or write on GET. Specify how old pending answer receipts resolve without repeating completion.
- [x] 3.2 BE: item/topic result and official-current-result resolution across catalog/progress/actions implemented in4b9cfea. Real clone run completed5 items/30 quizzes, unlocked each next item, and returned topic27/30=90%; completed topic appeared in Mine and stayed out of Discover. User main next-item button opened CONTEXT/0. Hidden lifecycle and retry acceptance remain C.
- [x] 3.3 FE: item/topic result routes and server fractions/percentages implemented. Browser main legacy recovery showed3/6=50%, reload retained result, and next action started the allowed item. Clone browser showed topic27/30=90%; fresh final answer held feedback at100% until Continue opened6/6=100% result. Mobile390x844 result layout checked. Review/retry only use existing allowed action entries; their full lifecycle is not implemented here.
  Remove the misleading endless retry path for a confirmed completion-contract mismatch. Do not accept IN_PROGRESS + quiz:null as completed or navigate to an invented result; use the BE recovery contract for the already-stuck attempt.
- [x] 3.4 Integration: real existing clone full-topic flow passed184 HTTP requests; final completion/result/replay/next permissions and aggregate counts matched OpenAPI. Main stuck attempt recovered without new submissions; old answer receipt retained revision18/91%, new completion receipt retained revision19/100%. Latest build additionally passed concurrent quiz requests and fresh browser final correctness. No fixture/fake completion or DB reset; raw evidence is ignored under `.artifacts/topic-slice-b/`, tracked outcomes are in the sibling FE13 live report. No network response-loss injection or C acceptance claimed.

## 4. Slice C — review, retry and current visibility

- [ ] 4.1 BE: implement read-only item review from current completed reference, include solved quiz feedback/first-try flags and MINOR content; verify unfinished retry leaves old review intact, superseded result returns RESULT_CHANGED and GET writes nothing.
- [ ] 4.2 BE: implement ITEM/TOPIC retry sessions with frozen versions/release/items, observed-session replacement and resume. Verify a stale replacement cannot abandon another session; completed retry replaces the previous score even if lower, abandoned unfinished item leaves old score intact.
- [ ] 4.3 BE: apply shared effective-visibility policy to all learner reads/writes and explicit hidden-session reconciliation; verify hidden active attempts cannot block next visible item, hidden retries are skipped without fake results and no GET creates the next attempt.
- [ ] 4.4 FE: reuse lesson/quiz presentation in review with all inputs read-only, wire confirmation/resume/restart actions and frozen topic retry progression. Verify normal running mode still cannot reopen solved quizzes and result reference changes cause a safe reload.
- [ ] 4.5 FE: handle ITEM_HIDDEN during content/quiz/feedback/result/review, stop audio/selection, notify then return to detail and refresh progress/mine. Verify one actual admin hide is honored by BE after the next request; no mock responses or polling loop.
- [ ] 4.6 Integration: demonstrate retry resume, lower-score replacement, one stale-session conflict, level denied only for new starts and hidden-item rejection. Use stored local profiles (A1/A2/B1 as needed), do not fabricate identities or seed completed results to claim a learning flow passed.

## 5. Verification and handoff

- [ ] 5.1 BE: run targeted real-persistence critical checks once after related changes, then applicable architecture/build checks. For application changes follow repo DoD; if full mvnw verify would require mocked suites forbidden by the user, disclose it as unrun rather than weakening checks or claiming full verification. BE targeted checks done: `TopicQuizMapperTest` 32/32, `TopicQuizAnswerTest` 20/20, `TopicQuizReadTest` 30/30, `ArchitectureTest` 9/9; pre-existing red `mvnw verify` baseline `246 run, 3 failures, 47 errors` unchanged (handoff §11). FE checks and full DoD still open.
- [ ] 5.2 FE: run build, lint and generated-type drift check after final related changes. FE `b841354`: build PASS, lint --quiet PASS (full lint previously 0 errors/70 existing warnings), type drift PASS (51 schemas). Browser error/content-return path checked; complete keyboard/pending/feedback/mobile quiz acceptance once real quiz data is available. Rerun checks after B changes; do not add fixture/mock tests or a repeated browser matrix without a new failure/question.
- [ ] 5.3 Documentation: update live evidence with exact paths/statuses/IDs and checks actually observed, link data pack manifest and its import state. Verify docs links, OpenSpec strict validation and git diff --check; leave unobserved scenarios explicitly unverified. BE-side evidence updated in `docs/topic/quiz-api-handoff.md`; data-pack manifest/import state not available yet.
- [ ] 5.4 Git: stage only task-owned files and commit each coherent slice in each touched repo using docs/feat/fix/test as appropriate. Verify git status after commits; preserve unrelated untracked files. Do not push, merge or archive this change without a separate request. BE Slice A committed as `b8c89a4` (quiz routes + tests), `12e96de` (frontier gate fix), `ed90ee1`, `7169bf3`, `50346f1` (docs); FE not touched; nothing pushed.
