# Package structure

One Maven module. One package level per feature. Layers live inside each feature.

```
fit.iuh.engolearn
├── <feature>                 e.g. topic, vocabulary, assessment
│   ├── contract              PUBLIC to other features: interfaces, DTOs, events, contract exceptions
│   ├── controller            HTTP endpoints for the frontend
│   ├── dto
│   │   ├── request
│   │   └── response          web DTOs, used only by this feature's controllers
│   ├── service               business logic; also holds the implementations of contract interfaces
│   ├── repository            persistence access
│   └── model                 entities / documents
├── shared                    cross-cutting code with NO dependency on any feature
│   └── exception, util, ...  base exceptions, global handler, generic helpers
├── config                    Spring configuration / wiring (may depend on anything)
└── EngolearnApplication
```

Tests mirror the same packages under `src/test/java`.

## What each layer may depend on

| Layer | May use | Must not use |
|---|---|---|
| controller | service, dto, own contract | repository, model (return dto, never entities) |
| service | repository, model, dto where needed, other features' `contract` | other features' anything-else |
| repository | model | service, controller |
| contract | `shared`, JDK, validation annotations | model, repository, service, controller, dto |

## Two kinds of DTO, do not mix them

- `dto/request|response`: shaped for the frontend. Free to change with the UI. Private to the feature.
- `contract/*Dto`: shaped for other features. Small and stable. Treat as a public API:
  add fields freely, do not rename, remove or change the meaning of fields without checking all callers.

## Where does new code go?

| I am adding... | Put it in |
|---|---|
| An endpoint for the frontend | `<feature>/controller` (+ `dto`) |
| Business logic | `<feature>/service` |
| Something another feature needs from mine | an interface + DTO in `<feature>/contract`, implemented in `<feature>/service` |
| An exception thrown through a contract method | `<feature>/contract` |
| An exception used only inside the feature | `<feature>/service` (or a sibling `exception` package) |
| A helper used by 2+ features that knows nothing about any feature | `shared` |
| A helper that knows about one feature's concepts | that feature, even if another feature wants it (expose via `contract`) |

Do not put something in `shared` only to dodge the cross-feature rule. If it knows business concepts,
it belongs to a feature.

## Naming

- Package names: lowercase, no underscores (`learningassistant`, not `learning_assistant`).
- Contract interfaces are named by capability: `TopicQuery`, `ProgressTracker`.
  Implementations: `TopicQueryImpl` in `service`.
- Contract DTOs: `<Thing>Dto` or Java records.

## Adding a new feature

1. Create `fit.iuh.engolearn.<feature>` with the layers it needs (skip empty ones).
2. Add a `contract` package only when another feature actually needs something from it.
3. Declare which features it depends on in `cross-feature-communication.md` (allowed dependencies table).
4. Run `ArchitectureTest`.
