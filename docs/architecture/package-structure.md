# Package structure

The application is a single Maven module. Organize production code by **feature first, layer second**. Create only the layers a feature needs; do not add empty placeholder packages.

```text
fit.iuh.engolearn
├── <feature>                 current features: topic, dictionary, user
│   ├── contract              public capability interfaces and contract types
│   │   └── dto               DTOs shared through that contract
│   ├── controller            frontend HTTP endpoints
│   ├── dto
│   │   ├── request           frontend request DTOs
│   │   └── response          frontend response DTOs
│   ├── service               business logic and contract implementations
│   ├── repository            persistence access
│   ├── model                 feature-owned entities/documents and domain types
│   └── exception             optional; only for feature-local exceptions
├── shared                    cross-cutting types with no feature knowledge
├── config                    application configuration and wiring
└── EngolearnApplication
```

Tests mirror production packages under `src/test/java`.

## Dependency policy

| Area | May depend on | Must not depend on |
|---|---|---|
| `controller` | Its feature's service and web DTOs | Its own repository or model directly |
| `service` | Its feature's repository, model, DTOs, and contracts | See [cross-feature communication](cross-feature-communication.md) for dependencies on other features |
| `repository` | Its feature's model and persistence support | Controllers or services |
| `contract` | Small contract types; JDK, `shared`, and validation types as needed | Entities, repositories, business logic, web DTOs, or framework-heavy types |
| `shared` | Cross-cutting types that do not know about features | Any feature package |
| `config` | Types needed for application wiring | Business logic that belongs in a feature |

Controllers should return response DTOs, not persistence entities. A contract is a feature's public in-process API; its implementation belongs in that feature's `service` package.

## Two kinds of DTO

- **Web DTO** — `<feature>.dto.request|response`; shaped for a frontend/API contract and private to that feature.
- **Contract DTO** — `<feature>.contract.dto`; a small, stable type exchanged between features. Treat it as a public API: check callers before changing field meaning, removing a field, or renaming it.

Do not place a feature-specific concept in `shared` merely to bypass a dependency boundary. Keep it in its owning feature and expose only the capability needed by other features.

## Where new code goes

| Adding | Put it in |
|---|---|
| Frontend endpoint | `<feature>/controller` and the relevant web DTOs |
| Business logic | `<feature>/service` |
| Capability another feature needs | Interface and contract types in `<feature>/contract`; implementation in `service` |
| Persistence access | `<feature>/repository` |
| Feature-owned entity or domain type | `<feature>/model` |
| Generic cross-feature utility | `shared`, only if it has no feature knowledge |
| Spring/application wiring | `config` |

Package names are lowercase and use no underscores. Add a new top-level feature only with approval; update the approved feature-root set in `ArchitectureTest` as part of that change.

## What is automatically enforced?

`src/test/java/fit/iuh/engolearn/ArchitectureTest.java` currently checks:

- approved top-level roots and feature-first package shape;
- cross-feature class dependencies target a `contract` package;
- `shared` does not depend on features;
- package slices are free of dependency cycles;
- dependencies used by contract classes stay within the ArchUnit allow-list;
- Controller, Service, and Repository layer dependency direction.

These checks do not prove every rule in this document. In particular, review is still needed to ensure controllers expose DTOs rather than entities, the approved dependency table is respected, and a refactor preserves external and persistence behavior. Do not describe a rule as automatically enforced unless a test actually checks it.
