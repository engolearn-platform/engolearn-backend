# Agent entry point

EngoLearn Backend is a Spring Boot modular monolith under `fit.iuh.engolearn`.

1. Start with the [documentation index](docs/README.md), [system overview](docs/architecture/system-overview.md) and [module map](docs/architecture/module-map.md).
2. Read the relevant module entry and flows; Subscription starts at [its technical README](docs/technical/subscription/README.md).
3. Follow the authoritative [package boundaries](docs/architecture/package-structure.md), [communication policy](docs/architecture/cross-feature-communication.md), [implementation conventions](docs/architecture/implementation-conventions.md) and [working agreement](docs/agent/working-agreement.md). These include dependency approval, behavior preservation, comments and public-contract requirements.
4. Inspect actual implementation, callers, persistence and tests before editing code or describing status. Do not invent business rules or treat legacy inconsistencies as exceptions.
5. Update the owning documentation when implementation changes, link instead of duplicating, and complete the applicable [Definition of Done](docs/agent/definition-of-done.md). Report actual checks, failures, unrun checks and manual steps.

Use the team's chosen workflow. OpenSpec is optional; when a task already uses it, read its proposal, design, applicable specs and tasks. `ArchitectureTest` enforces only a subset of policy; passing it does not replace the documents above.
