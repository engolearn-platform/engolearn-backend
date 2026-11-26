# Agent entry point

EngoLearn Backend is a Spring Boot modular monolith under `fit.iuh.engolearn`.

1. Start with the [documentation index](docs/README.md), [system overview](docs/architecture/system-overview.md) and [module map](docs/architecture/module-map.md).
2. Before exploring or editing a module, locate and read applicable module-level `AGENTS.md` files. Use the [technical documentation index](docs/technical/README.md) to find the relevant module entry and flows. Keep module-specific guidance in the module's own instructions or documentation.
3. Follow the authoritative [package boundaries](docs/architecture/package-structure.md), [communication policy](docs/architecture/cross-feature-communication.md), [implementation conventions](docs/architecture/implementation-conventions.md) and [working agreement](docs/agent/working-agreement.md). These include dependency approval, behavior preservation, comments and public-contract requirements.
4. Inspect actual implementation, callers, persistence and tests before editing code or describing status. Do not invent business rules or treat legacy inconsistencies as exceptions.
5. Update the owning documentation when implementation changes, link instead of duplicating, and complete the applicable [Definition of Done](docs/agent/definition-of-done.md). Report actual checks, failures, unrun checks and manual steps.
6. When creating or changing protected endpoints or role-gated service operations, developers and coding agents MUST read and follow the [security authorization rules](docs/architecture/security-authorization.md).

Use the team's chosen workflow. OpenSpec is optional; when a task already uses it, read its proposal, design, applicable specs and tasks. `ArchitectureTest` enforces only a subset of policy; passing it does not replace the documents above.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
