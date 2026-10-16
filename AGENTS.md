# AGENTS.md — engolearn-backend

Spring Boot modular monolith. Base package: `fit.iuh.engolearn`.
Small student team, so prefer simple and boring solutions.

## Read before changing structure or adding code

- `docs/architecture/package-structure.md` — where code goes
- `docs/architecture/cross-feature-communication.md` — how features talk to each other

## Hard rules (enforced by `ArchitectureTest`; the build fails if they are broken)

1. Package by feature first, layer second: `fit.iuh.engolearn.<feature>.<layer>`.
2. A feature may use another feature ONLY through that feature's `contract` package.
   Never import another feature's controller, service, repository, model or dto.
3. Never reference another feature's entity. Store its id and ask its contract when you need details.
4. `contract` contains only interfaces, plain DTOs, events, and exceptions that are part of the contract.
   No logic, no entities, no framework-heavy types.
5. `shared` must not depend on any feature.
6. No dependency cycles between features. If two features need each other, use an event or
   question the boundary. Ask before adding a cycle.

## Working agreements

- Refactors must not change behaviour, REST paths, request/response JSON, or DB collection/table/field names.
- Business rules live in the team's online docs, not in this repo. If a task depends on a rule you cannot
  find, ask. Do not invent rules.
- Do not add libraries, Maven modules, or new top-level packages without asking.
- Run `./mvnw verify` before finishing (use the project's actual build command if different).
- If a rule here blocks you, stop and ask. Do not work around it (no suppressions, no ArchUnit exclusions,
  no copying entities into another feature).
