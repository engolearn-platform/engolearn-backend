## Why

The backend follows flat n-layer packaging (`controller / service / repository / dto / models`), so every domain touches the same shared packages and cross-domain calls have no visible boundary. Moving to feature-first packages with an explicit `contract` per feature now is cheap (only ~50 model classes, 1 service interface, no controller/repository implementations yet) and makes a later microservices split possible without rewriting business logic.

## What Changes

- Move code from flat layers to feature packages `fit.iuh.engolearn.<feature>.<layer>` with three features: `topic`, `dictionary` (renamed from `vocabularies`), `user` (includes progress models + future simple auth; `identity/` idea merged in, no split).
- Introduce `shared` (same level as features) for cross-cutting code with no feature knowledge: base documents, shared enums, global exceptions, common API response shape, generic utils.
- Rename `configuaration` -> `config` (typo fix, package-only).
- Delete `models.learning_assistant.*` (`AiConversation`, `ContextSnapshotVo`, `ConversationMessageVo`, related enums) and empty placeholder dirs `ai/`, `ai/internal/`, `identity/` — **BREAKING** for the `ai_conversations` collection, accepted: dev stage, team drops the DB/collection manually, no code depends on it.
- Remove flat packages after the move: `controller/`, `dto/`, `service/`, `repository/`, `exception/`, `utils/`, `models/` (+ `.gitkeep` placeholders).
- Add per-feature `contract` package (sibling of `service`) holding only interfaces + `dto/` for contract DTOs; web DTOs (`dto/request|response`) stay private inside each feature and are never reused as contracts.
- Add ArchUnit guardrails (`ArchitectureTest` with `FreezingArchRule` first, then strict) enforcing: feature-first packaging, cross-feature use only via `contract`, `shared` depends on nothing, no feature cycles, contract self-containment, layer rules inside features.
- No behaviour change otherwise: no changed REST paths, request/response JSON, or DB collection/field names for the kept domains. No Maven multi-module, no Spring Modulith, no new runtime libraries (ArchUnit test-scope only).

## Capabilities

### New Capabilities

_None — pure structural refactor; no user-visible behavior is added._

### Modified Capabilities

_None — kept domains keep identical behavior; `learningassistant` removal is deletion of unused code with no consumers (see What Changes), not a requirement change to an existing spec._

> Note: this change sets `skip_specs: true` in `.openspec.yaml` (pure refactor + deletion of unused models, no spec-level behavior to capture).

## Impact

- Code: every file under `fit.iuh.engolearn` moves package (import + package declaration updates); `MongoAuditingConfig` moves to `config`.
- DB: package move changes default `_class` type hints — accepted, dev DB is dropped manually by the team (no `@TypeAlias`, no migration in code). Kept collections keep explicit names: `topics`, `topics_version`, `topic_items`, `vocabularies` (entities move to `dictionary.model` but collection names unchanged), `user_topic_progress`, `user_topic_item_progress`. Stale `ai_conversations` data is discarded manually.
- APIs: none yet (no controller implementations), so no REST/JSON break.
- Build: `./mvnw verify` must stay green throughout; `ArchitectureTest` added with frozen store first, strict at the end.
- Docs: `task/AGENTS.md`, `package-structure.md`, `cross-feature-communication.md` promoted into the repo (`AGENTS.md` at root, rest under `docs/architecture/`); `task/refactor-plan.md` retired when done.
