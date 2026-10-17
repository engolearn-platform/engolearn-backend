# Agent instructions

EngoLearn Backend is a Spring Boot modular monolith. Base package: `fit.iuh.engolearn`.

## Start here

Read only what applies to the task:

- `docs/architecture/package-structure.md` — package layout and layer boundaries.
- `docs/architecture/cross-feature-communication.md` — allowed dependencies between features.
- `docs/agent/working-agreement.md` — task workflow, OpenSpec, and implementation loop.
- `docs/agent/definition-of-done.md` — completion criteria and verification gates.

For an existing OpenSpec change, inspect its proposal, design, applicable specs, and tasks before implementation.

## Non-negotiable

- Follow the documented package and cross-feature boundaries.
- Refactors must preserve behavior and existing API/persistence contracts unless the approved scope explicitly says otherwise.
- Do not invent missing business rules. Find the team's business documentation or report the ambiguity.
- Do not add dependencies, Maven modules, or top-level feature packages without approval.
- Do not bypass architecture rules, suppress violations, or weaken tests just to make a check pass.

## Before declaring completion

Follow the working agreement and the applicable Definition of Done. Report actual verification results, including failures, checks not run, and outstanding manual steps.

`ArchitectureTest` enforces a subset of the architecture rules; the architecture documents remain the policy source for rules that are not automated.
