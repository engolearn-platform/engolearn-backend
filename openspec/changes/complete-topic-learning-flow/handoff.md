# Handoff — complete-topic-learning-flow

Status (coordination sync, 2026-10-28): BE Slice A code is committed in `b8c89a4` and `12e96de`, with documentation through `b14eff0`. Work continues on `feat/complete-topic-learning-flow`, created from `b14eff0`; the previous `feat/a1-cafe-sample-data` branch and its history remain intact. FE quiz 410 contract is committed in `82d2201`, and consumer code in `b841354`; task 1.2 is partial because full hidden coverage/retry recovery remain open. FE tasks 2.3/2.4 are implemented but not fully accepted. BE evidence has no live SITUATIONAL_CHOICE body; this is a pending check, not proof that all data blocks it. User reports both quiz types playable, with the last answer stuck. B/C code is not started. The change is NOT complete and must NOT be archived.

Next checkpoint: coordinate B completion/result with the remaining A acceptance checks. Preserve and recover the already-answered IN_PROGRESS attempt through an explicit idempotent mutation; no reset, no GET repair and no fake FE summary. See tasks 3.1/3.3. This synchronization changes documentation only; it does not approve a new persistence design or infrastructure change.
FE-facing API truth: `docs/topic/quiz-api-handoff.md`. Design deviations: `design.md` Amendment A.

## Required context

- Business repo (sibling): `docs/usecase_spec/topic-learning/00-module.md`; F02/F03/F04 rules/UC; F07 quiz/publish constraints; `docs/technical/topic-learning/learner-runtime-mapping.md`, schema v1 and explanatory data model. Business commit: `aaff29c`.
- BE observed baseline `4b36f15`: [live evidence](../../../docs/topic/live-integration.md); [data-pack artifact](../../../docs/topic/sample-data-plan.md). Existing untracked api_contract/material files are user-owned inputs, do not stage or overwrite them.
- FE observed baseline `a5d0b4a`: sibling `docs/features/topic/learner/10-completion-implementation-plan.md`, `docs/handout/topic-learning/api.openapi.yaml`, generated types and current live report.

## Agent instructions

Implement one end-to-end slice at a time. BE may own its tasks while the FE agent owns its consumer tasks, but neither may invent the other side or declare success from generated DTOs. Before a consumer task, confirm the real endpoint exists and returns expected stored data. Report a missing endpoint/content as a blocker; never launch mocks or fake auth/level providers. Sample-data subagent may author the files described in sample-data-plan; import remains explicit.

Critical checks are selected in tasks, not a mandate for a combinatorial test matrix. No repeated broad tests/browser loops without a new change or failure. Documentation-only preparation needs OpenSpec/link/diff checks, not Maven/FE test runs. Keep actual evidence separate from planned scenarios.

Commit coherent work in each modified repo with the matching type (docs for artifacts, feat/fix for implementation, test only for a test-only change). Stage intended files only, preserve user changes, no push/merge/archive without a separate instruction. A later implementation request is required after artifact review.
