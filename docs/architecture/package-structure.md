# Package structure

The application is a single Maven module. Organize production code by **feature first, layer second**. Create only the layers a feature needs; do not add empty placeholder packages.

This document owns package placement, package responsibilities, allowed dependencies and the current automated architecture checks. Coding and representation rules live in [implementation conventions](implementation-conventions.md); communication semantics and approved feature dependencies live in [cross-feature communication](cross-feature-communication.md).

The structure is required for new code and code being refactored. Existing code may still need migration; do not treat its placement as an exception to these rules.

```text
fit.iuh.engolearn
├── <feature>                 current features: topic, dictionary, user, learningpath, subscription, quota, payment
│   ├── contract              public capability interfaces
│   │   ├── dto               public input/result DTOs
│   │   ├── event             published facts
│   │   ├── exception         public caller-facing failures
│   │   └── enums             public finite values, when needed
│   ├── controller            frontend HTTP endpoints
│   │   └── exceptionhandler  feature HTTP exception advice
│   ├── dto
│   │   ├── request           frontend request DTOs
│   │   └── response          frontend response DTOs
│   ├── service               feature-local business interfaces
│   │   └── impl              business and contract implementations
│   ├── repository            persistence access
│   ├── mapper                feature-local DTO/VO mapping helpers
│   ├── model                 feature-owned entities/documents and domain types
│   │   ├── enums             feature-owned persisted states and classifications, when needed
│   │   └── vo                embedded document types, when needed
│   ├── infra                 technical adapters
│   │   └── scheduling        scheduled triggers, when needed
│   ├── exception             optional; only for feature-local exceptions
│   └── utils                 optional; feature-local helpers and their abstractions
├── shared                    cross-cutting types with no feature knowledge
├── config                    application configuration and wiring
└── EngolearnApplication
```

Tests mirror production packages under `src/test/java`.

The `service` root contains feature-local business interfaces; their implementations and public contract implementations belong in `service.impl`. Do not add a duplicate local interface solely to mirror a public contract.

Database collection access, query/BSON construction, update predicates and compare-and-set operations belong in `repository`. Scheduled trigger adapters belong in `infra.scheduling`; scheduling enablement and application wiring belong in `config`. `infra` is not a replacement for repositories or a general helper package.

HTTP DTOs belong in `dto.request` / `dto.response`, not as nested controller types. Embedded persistence values belong in separate files in `model.vo`, not as nested persistence declarations.

Feature-local exceptions belong in `exception`; HTTP advice belongs in `controller.exceptionhandler`. For per-controller advice, the only accepted locations are the target controller's package and its exact `.exceptionhandler` child. New handlers use the child package; existing colocated advice remains accepted. Arbitrary packages elsewhere in the same feature are not accepted.

Create a new top-level feature only with approval and update the approved feature-root set in `ArchitectureTest` as part of the change. Naming syntax is defined in [implementation conventions](implementation-conventions.md#1-names).

## Dependency policy

| Area | May depend on | Must not depend on |
|---|---|---|
| `controller` | Its feature's service and web DTOs | Its own repository or model directly |
| `service` | Its feature's repository, model, DTOs, mappers, helper abstractions, business interfaces and contracts; suitable `shared` types | Concrete collaborating business implementations; see [cross-feature communication](cross-feature-communication.md) for dependencies on other features |
| `repository` | Its feature's model and persistence support | Controllers or services |
| `mapper` | Its feature's DTOs and model types for transformation | Controllers, services, repositories, or business transitions |
| `model` | Its feature's domain/value types, `shared`, persistence annotations and JDK/Lombok support; public contract types only with explicit compatibility consideration | Web DTOs, controllers, services, repositories or infrastructure |
| Web `dto` | Web/contract value types, owned enums, suitable `shared` types, JDK, validation and Lombok support | Persistence entities, controllers, services or repositories |
| `contract` | Small contract types; JDK, `shared`, validation and Lombok support as needed | Entities, repositories, business logic, web DTOs, or framework-heavy types |
| `infra` | Owned capability interfaces, repositories, model/contract types and technical support needed by the adapter | Business rules or direct access to another feature's internals |
| `utils` | Feature-local helper types and the abstractions they implement | Controllers, repositories, or business logic |
| `shared` | Cross-cutting types that do not know about features | Any feature package |
| `config` | Types needed for application wiring | Business logic that belongs in a feature |

## Two kinds of DTO

- **Web DTO** — `<feature>.dto.request|response`; shaped for a frontend/API contract and private to that feature.
- **Contract DTO** — `<feature>.contract.dto`; data exchanged through a feature's public in-process interface. Compatibility rules live in [cross-feature communication](cross-feature-communication.md#public-contract-semantics).

Do not place a feature-specific concept in `shared` merely to bypass a dependency boundary. Keep it in its owning feature and expose only the capability needed by other features.

## Public contract layout

The tree above is the package source of truth. Within each feature's `contract`, use these roles:

| Package | Contains |
|---|---|
| `contract` root | Public capability interfaces only |
| `contract.dto` | Public input/result types; do not subdivide by HTTP `request` / `response` |
| `contract.event` | Event declarations owned by the publishing feature |
| `contract.exception` | Public failures callers need to handle |
| `contract.enums` | Finite values forming part of the public contract, only when needed |

Implementation classes and Spring beans/listeners MUST NOT live anywhere inside `contract`. Event consumers stay outside it in the receiving feature. Public contract enums MUST NOT be imported from `model.enums`.

Suffixes and data representation are defined in [implementation conventions](implementation-conventions.md#6-public-data-representations); caller/provider obligations are defined in [cross-feature communication](cross-feature-communication.md#public-contract-semantics).

## What is automatically enforced?

`src/test/java/fit/iuh/engolearn/ArchitectureTest.java` currently checks:

- approved top-level roots and feature-first package shape;
- cross-feature class dependencies target a `contract` package;
- `shared` does not depend on features;
- package slices are free of dependency cycles;
- dependencies used by contract classes stay within the ArchUnit allow-list;
- Controller, Service, and Repository layer dependency direction;
- every public method of a `@RestController` returns the shared `ApiResponse` envelope;
- per-controller `@RestControllerAdvice` handlers live in the controller package or its exact `exceptionhandler` child and return `ProblemDetail` (RFC 7807);

These checks do not prove every documented rule. They do not enforce DTO payload types/suffixes, contract subpackage roles, business interfaces/`impl`, Lombok injection, Mongo isolation, model/VO representation/mappings, scheduler placement, or the full dependency policy. Review those against the relevant document; do not describe a rule as automatically enforced unless a test actually checks it. The exact approved feature-dependency table has its own enforcement caveat in [cross-feature communication](cross-feature-communication.md#approved-feature-dependencies).
