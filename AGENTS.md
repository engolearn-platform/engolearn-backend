# Agent instructions

EngoLearn Backend is a Spring Boot modular monolith. Base package: `fit.iuh.engolearn`.

## Start here

Read only what applies to the task:

- `docs/architecture/package-structure.md` — package layout and layer boundaries.
- `docs/architecture/cross-feature-communication.md` — allowed dependencies between features.
- `docs/architecture/implementation-conventions.md` — required naming, responsibility, model and public-contract conventions for new/refactored code.
- `docs/agent/working-agreement.md` — workflow-neutral task process and implementation loop.
- `docs/agent/definition-of-done.md` — completion criteria for every workflow.

Use the team's chosen workflow. OpenSpec is an optional pilot, not mandatory for every task. If the task already uses OpenSpec, inspect its proposal, design, applicable specs, and tasks before implementation.

## Non-negotiable

- Follow the documented package and cross-feature boundaries.
- Follow the implementation conventions; inspect existing patterns without treating legacy inconsistencies as exceptions.
- Refactors must preserve behavior and existing API/persistence contracts unless the agreed scope explicitly says otherwise.
- Do not invent missing business rules. Find the team's business documentation or report the ambiguity.
- Do not add dependencies, Maven modules, or top-level feature packages without approval.
- Do not bypass architecture rules, suppress violations, or weaken tests just to make a check pass.

## Code comments

- Default to no comments: names and structure should carry the intent; add a comment only for a non-obvious *why* (constraint, invariant, ordering, workaround, trade-off), max 1-2 lines.
- Never restate what the code says: no "Gets the X" narration, no `@param`/`@return` boilerplate; reserve Javadoc for public cross-feature API, at most 1-3 lines.
- Tests carry intent in their names: comment only non-obvious setup or a regression rationale; when unsure, delete the comment.

## Before declaring completion

Follow the working agreement and the applicable Definition of Done, regardless of workflow. Report actual verification results, including failures, checks not run, and outstanding manual steps.

`ArchitectureTest` enforces a subset of the architecture rules; the architecture documents remain the policy source for rules that are not automated.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
