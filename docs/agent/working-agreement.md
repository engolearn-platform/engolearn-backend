# Working agreement

This document defines **how work is performed**, regardless of which team workflow is used. Completion criteria live in [Definition of Done](definition-of-done.md); package and dependency policy lives in `docs/architecture/`.

## 1. Understand the task

1. Confirm the requested scope and inspect the relevant code, tests, and documentation.
2. Use the team's chosen workflow for the task. OpenSpec is an optional pilot workflow, not a repository-wide requirement.
3. When a task uses OpenSpec, read its proposal, design, applicable specs, and tasks before changing code; keep those artifacts aligned with the approved scope and implementation.
4. For tasks using another workflow, use the available issue, task, PR description, or agreed notes to understand the scope and expected outcome. Do not create OpenSpec artifacts just to satisfy this document.
5. Do not invent business rules. Find the team's source documentation or report the ambiguity before relying on an assumption.

## 2. Implement in small steps

- Prefer small, reviewable changes that follow the approved scope and architecture.
- Update or add automated tests where they can verify the intended behavior or prevent regression.
- After each meaningful step, run the relevant checks and inspect their output before proceeding.
- If a check fails, read the failure, identify the cause, make a targeted fix, and rerun it. Do not remove, weaken, suppress, or bypass a check merely to obtain a passing result.
- If the cause is unclear, a required decision is missing, or repeated attempts make no progress, stop and report the blocker instead of looping indefinitely.

## 3. Workflow-specific handling

- **OpenSpec:** use its validation and archive commands when applicable to the change. Archive only after the applicable Definition of Done is satisfied; archiving is not verification.
- **Other workflows:** follow the team's agreed review and completion process. Do not create, validate, or archive OpenSpec artifacts unless the task explicitly calls for them.
- In any workflow, a checked task box is a status record, not proof that a check ran. Mark work complete only when the work is done and the required evidence has been checked.

## 4. Finish with evidence

For a change that modifies application code or configuration, run `./mvnw verify` as the final change-level verification when the environment supports it. During implementation, run faster, relevant checks after meaningful steps. For documentation-only changes, verify the changed paths, links, commands, and examples as appropriate; do not run the application test suite without a reason.

The final report must distinguish:
- checks actually run and their observed results;
- checks that failed or were not run;
- manual actions still required;
- known limitations or unresolved blockers.

Never claim that a check passed without observing a passing result. Use [Definition of Done](definition-of-done.md) to determine the applicable completion criteria for the task type, regardless of workflow.
