# Handoff — complete-topic-learning-flow

Status: planning artifacts were the state at `fdf28d3`. BE Slice A (tasks 1.4a, 2.1a, 2.2) is now implemented, reviewed and committed locally as `b8c89a4` (quiz routes + tests) and `12e96de` (frontier gate fix), `ed90ee1`, `7169bf3`, `50346f1` (docs). `1.2` is not started: the FE contract still lacks `410 ITEM_HIDDEN`. `2.1b` is blocked: no real SITUATIONAL_CHOICE response was captured, and reaching a complete one needs data work because 90/105 SITUATIONAL questions lack `quiz_data.scenario_vi` (handoff §12 item 3). Slices B/C are untouched. The change is NOT complete and must NOT be archived. Read proposal -> five delta specs -> design -> tasks before changing code.
FE-facing API truth: `docs/topic/quiz-api-handoff.md`. Design deviations: `design.md` Amendment A.

## Required context

- Business repo (sibling): `docs/usecase_spec/topic-learning/00-module.md`; F02/F03/F04 rules/UC; F07 quiz/publish constraints; `docs/technical/topic-learning/learner-runtime-mapping.md`, schema v1 and explanatory data model. Business commit: `aaff29c`.
- BE observed baseline `4b36f15`: [live evidence](../../../docs/topic/live-integration.md); [data-pack artifact](../../../docs/topic/sample-data-plan.md). Existing untracked api_contract/material files are user-owned inputs, do not stage or overwrite them.
- FE observed baseline `a5d0b4a`: sibling `docs/features/topic/learner/10-completion-implementation-plan.md`, `docs/handout/topic-learning/api.openapi.yaml`, generated types and current live report.

## Agent instructions

Implement one end-to-end slice at a time. BE may own its tasks while the FE agent owns its consumer tasks, but neither may invent the other side or declare success from generated DTOs. Before a consumer task, confirm the real endpoint exists and returns expected stored data. Report a missing endpoint/content as a blocker; never launch mocks or fake auth/level providers. Sample-data subagent may author the files described in sample-data-plan; import remains explicit.

Critical checks are selected in tasks, not a mandate for a combinatorial test matrix. No repeated broad tests/browser loops without a new change or failure. Documentation-only preparation needs OpenSpec/link/diff checks, not Maven/FE test runs. Keep actual evidence separate from planned scenarios.

Commit coherent work in each modified repo with the matching type (docs for artifacts, feat/fix for implementation, test only for a test-only change). Stage intended files only, preserve user changes, no push/merge/archive without a separate instruction. A later implementation request is required after artifact review.
