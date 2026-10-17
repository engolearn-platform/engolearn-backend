# Working agreement

This document defines **how work is performed**. Completion criteria live in [Definition of Done](definition-of-done.md); package and dependency policy lives in `docs/architecture/`.

## 1. Understand the task

1. Confirm the requested scope and inspect the relevant code, tests, and documentation.
2. For an existing OpenSpec change, read its `proposal.md`, `design.md`, applicable specs, and `tasks.md` before changing code.
3. Use the default `spec-driven` workflow for changes that need explicit requirements and design. A pure refactor may use `skip_specs: true` when behavior requirements do not change; still document scope, decisions, risks, and verification.
4. Do not invent business rules. Find the team's source documentation or report the ambiguity before relying on an assumption.

## 2. Implement in small steps

- Prefer small, reviewable changes that follow the approved scope and architecture.
- Update or add automated tests where they can verify the intended behavior or prevent regression.
- After each meaningful step, run the relevant checks and inspect their output before proceeding.
- If a check fails, read the failure, identify the cause, make a targeted fix, and rerun it. Do not remove, weaken, suppress, or bypass a check merely to obtain a passing result.
- If the cause is unclear, a required decision is missing, or repeated attempts make no progress, stop and report the blocker instead of looping indefinitely.

## 3. Use OpenSpec honestly

- Keep proposal, design, specs, and tasks aligned with the approved scope and actual implementation.
- Mark a task complete only when its work is done and the required evidence has been checked. A checked box is a status record, not proof that a test ran.
- Validate the change with the repository's OpenSpec tooling when applicable. Archive only after the applicable Definition of Done is satisfied; archiving is not verification.

## 4. Finish with evidence

Before reporting completion, run `./mvnw verify` unless the environment prevents it. Use the project's actual equivalent command if needed.

The final report must distinguish:
- checks actually run and their observed results;
- checks that failed or were not run;
- manual actions still required;
- known limitations or unresolved blockers.

Never claim that a check passed without observing a passing result. Use [Definition of Done](definition-of-done.md) to decide which additional checks apply to the task type.
