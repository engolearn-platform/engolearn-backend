## Status — Slice A BE (2026-10-28)

| Task | Status |
|---|---|
| 1.1 | partial |
| 1.2 | not started |
| 1.3 | not started |
| 1.4a | done |
| 1.4b | not started |
| 1.5 | not started |
| 2.1a | done |
| 2.1b | blocked |
| 2.1c | not started |
| 2.2 | done |
| 2.3 | not started |
| 2.4 | not started |
| 2.5 | not started |
| 3.1 | not started |
| 3.2 | not started |
| 3.3 | not started |
| 3.4 | not started |
| 4.1 | not started |
| 4.2 | not started |
| 4.3 | not started |
| 4.4 | not started |
| 4.5 | not started |
| 4.6 | not started |
| 5.1 | partial |
| 5.2 | not started |
| 5.3 | partial |
| 5.4 | partial |

## 1. Contract and compatible persistence foundation

- [ ] 1.1 BE/FE: read proposal/specs/design, current live-integration report and FE plan; record actual running BE/proxy URLs and route availability once. Verify viewer is the selected DB profile and report missing endpoints instead of starting a mock preview. BE route-availability part is recorded in `docs/topic/quiz-api-handoff.md` §1; the FE proxy confirmation is still open.
- [ ] 1.2 FE: sync OpenAPI hidden errors and explicit retry recovery fields defined in design, regenerate topic-api.types.ts, adapt client signatures only if needed. Verify topic:types:check and JSON parse; record the contract commit for BE. Not started — FE contract still lacks `410 ITEM_HIDDEN` (handoff §5/§7).
- [ ] 1.3 BE: implement topic-owned runtime aggregate/result storage with unique owner/topic index and compatibility reader; verify a legacy standalone LEARN and existing completed reference retain IDs, pinned version, revision and position without DB reset. Deliberately NOT built in Slice A; quiz mutations use single-document CAS on `UserTopicItemAttempt` instead — see `design.md` Amendment A1. Remaining scope moves with Slice B.
- [x] 1.4a BE: quiz answer mutations go through attempt CAS with request fingerprint/replay receipts. Evidence: handoff §9 decision 1 (CAS + receipts), §11 (replay adds no submission), §4/§6.6 (stale revision 409).
- [ ] 1.4b BE: route explicit start/advance/access/retry mutations through aggregate CAS with request fingerprint/replay receipts, migrating on mutation only. Not migrated: start/content/advance/access still use the existing per-attempt path. Verify GET is read-only, replay does not advance twice and stale revision does not mutate.
- [ ] 1.5 Data author: deliver the isolated sample pack specified in docs/topic/sample-data-plan.md; verify dry-run and rerun behavior against a separately selected local DB. Do not import into the user's DB automatically; hand over the command and manifest. Sample pack not delivered; user-owned sample files untouched.

## 2. Slice A — current quiz and persisted answers

- [x] 2.1a BE: normalize pinned module/questions/options with stable IDs and redact correctness/explanation from the current-quiz response. Verified on one real FILL_BLANK response with no answer/explanation leak (handoff §3.1, §11).
- [ ] 2.1b BE: verify one real SITUATIONAL_CHOICE response with no leaked answer fields. NOT done: no real SITUATIONAL_CHOICE response was captured, and reaching a complete one needs data work because 90/105 SITUATIONAL questions lack `quiz_data.scenario_vi` (handoff §12 item 3).
- [ ] 2.1c BE: redact correctness/explanation from the content response and reject ambiguous legacy answers (`answer_data` / `correct`). Not implemented or unverified: content redaction was never exercised against real persistence, and `TopicQuizMapper` reads only `is_correct`, so legacy ambiguity is neither detected nor rejected. Open decision for Slice B, not a verified behaviour.
- [x] 2.2 BE: implement current-quiz and answer routes with revision/current-question validation, persisted first-try failure and atomic frontier/receipt update. Verify wrong -> reload -> correct retains first-try failure; same-key replay adds no submission; stale different-key request returns409. Done — evidence: wrong -> reload -> correct keeps the first-try failure through `TopicQuizAnswerTest.wrongThenCorrectAppendsSecondSubmissionWithoutTouchingTheLoadedAttempt`; same-key replay adds no submission (handoff §11); stale revision returns 409 (handoff §6.6 real table); hidden item returns 410 and adds no submission (handoff §6.6, §11); pre-frontier requests return 409 (handoff §4, §11 smoke).
- [ ] 2.3 FE: add quiz state and two quiz renderers to TopicLessonView/useTopicLesson; use existing mutation journal and busy selection lock. Verify one real choice reaches BE/DB, wrong permits retry, network uncertainty retains key, correct shows returned explanation/audio without auto-next.
- [ ] 2.4 FE: enforce running-quiz frontier in topic-lesson helpers/nav/URL, flatten EXPRESSIONS purposes without group UI, keep collapsible part navigation and header progress. Verify content review/return preserves quiz frontier and solved quiz URLs cannot reopen a question.
- [ ] 2.5 Integration: demonstrate real FE proxy -> BE -> DB for both types, reload after wrong and after correct-before-Continue. Record requests/results once, commit code in each changed repo; do not mark later result/retry work done.

## 3. Slice B — final correctness and completion

- [ ] 3.1 BE: prepare immutable final result and publish via one runtime CAS including final accepted answer/result pointer/session cursor/receipt. Materialize legacy projections only afterward. Verify interruption after prepare and after CAS returns uncommitted-safe or fully committed state, with no lost result/duplicate finish.
- [ ] 3.2 BE: implement item/topic result and official-current-result resolution across catalog/progress/actions, visible-item counts, next-item permissions and aggregate accuracy. Verify final correct unlocks next item and last visible item moves topic to COMPLETED; aggregate count ratios before rounding.
- [ ] 3.3 FE: add item result and topic result views using actual DTO fields, connect final feedback Continue/reload and legal next-item/detail actions. Verify no score is calculated from local selections and My Topics/detail refetch after completion.
- [ ] 3.4 Integration: run one item end-to-end using the valid real data pack and inspect result/progress/next-item responses. Verify replay of final answer is stable, commit coherent BE/FE changes and record limitations; do not run the whole topic repeatedly just for screenshots.

## 4. Slice C — review, retry and current visibility

- [ ] 4.1 BE: implement read-only item review from current completed reference, include solved quiz feedback/first-try flags and MINOR content; verify unfinished retry leaves old review intact, superseded result returns RESULT_CHANGED and GET writes nothing.
- [ ] 4.2 BE: implement ITEM/TOPIC retry sessions with frozen versions/release/items, observed-session replacement and resume. Verify a stale replacement cannot abandon another session; completed retry replaces the previous score even if lower, abandoned unfinished item leaves old score intact.
- [ ] 4.3 BE: apply shared effective-visibility policy to all learner reads/writes and explicit hidden-session reconciliation; verify hidden active attempts cannot block next visible item, hidden retries are skipped without fake results and no GET creates the next attempt.
- [ ] 4.4 FE: reuse lesson/quiz presentation in review with all inputs read-only, wire confirmation/resume/restart actions and frozen topic retry progression. Verify normal running mode still cannot reopen solved quizzes and result reference changes cause a safe reload.
- [ ] 4.5 FE: handle ITEM_HIDDEN during content/quiz/feedback/result/review, stop audio/selection, notify then return to detail and refresh progress/mine. Verify one actual admin hide is honored by BE after the next request; no mock responses or polling loop.
- [ ] 4.6 Integration: demonstrate retry resume, lower-score replacement, one stale-session conflict, level denied only for new starts and hidden-item rejection. Use stored local profiles (A1/A2/B1 as needed), do not fabricate identities or seed completed results to claim a learning flow passed.

## 5. Verification and handoff

- [ ] 5.1 BE: run targeted real-persistence critical checks once after related changes, then applicable architecture/build checks. For application changes follow repo DoD; if full mvnw verify would require mocked suites forbidden by the user, disclose it as unrun rather than weakening checks or claiming full verification. BE targeted checks done: `TopicQuizMapperTest` 32/32, `TopicQuizAnswerTest` 20/20, `TopicQuizReadTest` 30/30, `ArchitectureTest` 9/9; pre-existing red `mvnw verify` baseline `246 run, 3 failures, 47 errors` unchanged (handoff §11). FE checks and full DoD still open.
- [ ] 5.2 FE: run build, lint and generated-type drift check after final related changes. Verify keyboard focus, busy/error states and one mobile layout on real screens. Do not add fixture/mock tests or run a repeated browser matrix without a new failure/question.
- [ ] 5.3 Documentation: update live evidence with exact paths/statuses/IDs and checks actually observed, link data pack manifest and its import state. Verify docs links, OpenSpec strict validation and git diff --check; leave unobserved scenarios explicitly unverified. BE-side evidence updated in `docs/topic/quiz-api-handoff.md`; data-pack manifest/import state not available yet.
- [ ] 5.4 Git: stage only task-owned files and commit each coherent slice in each touched repo using docs/feat/fix/test as appropriate. Verify git status after commits; preserve unrelated untracked files. Do not push, merge or archive this change without a separate request. BE Slice A committed as `b8c89a4` (quiz routes + tests), `12e96de` (frontier gate fix), `ed90ee1`, `7169bf3`, `50346f1` (docs); FE not touched; nothing pushed.
