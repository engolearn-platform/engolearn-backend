## Context

See `proposal.md` for motivation. Current state (verified on disk, differs slightly from `task/refactor-plan.md` guesses):

- `fit.iuh.engolearn` holds ~50 model classes already grouped by domain under `models/` (`topics`, `vocabularies`, `users`, `learning_assistant`, `shared`), plus 6 flat web DTOs under `dto/`, 1 service interface (`TopicBrowseService`), and empty placeholders (`controller/`, `repository/`, `exception/`, `utils/`, `identity/user/`, `ai/internal/`, `shared/util/` with `.gitkeep`).
- `configuaration/MongoAuditingConfig.java` (typo) is the only config class; `EngolearnApplication` uses default `@SpringBootApplication` scanning.
- Cross-domain coupling is visible at field level even without service impls: `UserTopicProgress`/`UserTopicItemProgress` already store `topic_id`/`topic_item_id` as plain `String` ids (good); `TopicItem.words[]` (`ItemWordVo`) stores `vocab_id` + `sense_id` + `WordSnapshotVo` snapshot; `AiConversation` stores `user_id` + `context_ref_id` + snapshot (to be deleted per proposal).
- `openspec/` has no specs; `task/` holds the brief (`AGENTS.md`, `package-structure.md`, `cross-feature-communication.md`, `refactor-plan.md`) to be promoted into the repo.

## Goals / Non-Goals

**Goals:**

- Every kept class has exactly one owner feature; the flat `controller/dto/service/repository/exception/utils/models` packages disappear.
- Each feature exposes at most one narrow `contract` (interfaces + `dto/`) and consumes others only through it; `shared` stays feature-ignorant; ArchUnit proves it (frozen first, strict at the end).
- The move itself introduces zero hidden persistence/wiring breaks that survive `verify` green.

**Non-Goals:**

- No logic, validation, or field changes inside stale models — package declarations + imports only (plus deletions listed in the proposal).
- No contract interfaces are *consumed* yet beyond scaffolding: with a single `TopicBrowseService` interface and no cross-feature service calls in code, Phase 3 of `task/refactor-plan.md` reduces to creating minimal contract shells + documenting the allowed-dependency table, not rewiring callers.
- No `@TypeAlias`, no data migration, no `basePackages` changes, no internal controllers/REST clients (future split work stays out).

## Decisions

**D1 — Feature set: `topic`, `dictionary`, `user` (+ `shared`, `config`). No `vocabulary`, no `learningassistant`, no `identity` split.**
Rationale: user decision from exploration — `word` is a real entity but lives in the `dictionary` domain now; `user` stays one bundle (progress + future simple auth); `learningassistant` models are deleted, not parked. Alternative (keep `vocabulary` name / split `auth`) rejected: rename cost is near-zero now and grows later; a premature `auth` split has no code to own.

**D2 — Target layout per feature: `<feature>/{contract(+dto/),controller,dto/request|response,service,repository,model}`; `shared/` and `config/` at the same level.**
Rationale: matches `task/package-structure.md` so the existing `ArchitectureTest` sketch applies unchanged; `contract` as a sibling of `service` (not nested under it) makes the "public vs private" boundary visible in the package tree. Empty layers are still created as packages only when the moved code needs them (no hollow dirs with `.gitkeep` farming). Alternative (contract nested in service) rejected: harder to enforce D6 with ArchUnit package matchers.

**D3 — Class-to-feature mapping (frozen for Phase 0 sign-off).**

| Current | Target |
|---|---|
| `models.topics.**` (Topic, TopicItem, TopicVersion, `vo/*`, `enums/*`) | `topic.model.*` (enums stay beside their aggregate; `vo` stays a sub-package of `model`) |
| `models.vocabularies.**` | `dictionary.model.*` |
| `models.users.**` | `user.model.*` |
| `models.shared.**` (BaseDocument, EmbeddedDocument, CefrLevel, ContentStatus, QuizType) | `shared.*` — BUT shared enums with domain meaning stay only if truly cross-feature; CefrLevel/ContentStatus/QuizType are used by topic today, so Phase 0 must confirm no feature-specific semantics leak into `shared` (if `QuizType` is topic-only, it moves to `topic.model`) |
| `models.learning_assistant.**` | DELETE (no target) |
| `dto.request.GetAvailableTopicRequest`, `GetTopicDetailRequest`, `dto.response.Topic*` | `topic.dto.request` / `topic.dto.response` |
| `dto.response.UserProgressSummaryResponse` | `user.dto.response` (it describes progress, not topic content; final placement confirmed in Phase 0 by reading its future use in `TopicBrowseService` composition) |
| `service.TopicBrowseService` | `topic.service.TopicBrowseService` (interface moves with its content domain; if its impl later composes progress, it consumes `user.contract`, never `user.service`) |
| `configuaration.MongoAuditingConfig` | `config.MongoAuditingConfig` |
| `exception/`, `utils/`, `shared/util/` placeholders | `shared.exception`, `shared.util` only for what actually exists; delete the rest |

**D4 — Deletion, not move, for `learningassistant`.**
Rationale: nothing injects or queries `AiConversation`; deleting ~7 files now is cheaper than carrying a fourth feature with no owner. The `ai_conversations` collection cleanup is manual by the team and outside this change (no code handles it). Alternative (park the models unmoved) rejected by the user.

**D5 — Persistence: explicit collection names stay, `_class` drift accepted, DB dropped manually.**
Rationale (R1 resolution): all kept entities already declare explicit `@Document` collection names, so package moves cannot rename collections. Default `_class` hints *will* change, which is safe only because the team drops the dev DB by hand after the move — therefore no `@TypeAlias`, no migration code (boring and correct for this stage). Rule going forward: collection names always explicit; never query on `_class`; any future entity-package move with real data requires its own migration change.

**D6 — Contract shape: `<feature>/contract` holds capability interfaces + `dto/` of small stable contract DTOs; web DTOs never cross.**
Rationale (R4 resolution): e.g. `dictionary.contract.VocabularyLookup` + `contract.dto.VocabSenseDto` (lookup by `vocab_id`/`sense_id` for topic authoring validation) vs `topic.dto.response.TopicDetailResponse` (frontend shape, private). Contract methods are designed as if remote someday: `Optional`/contract exception, no persistence leakage. The allowed-dependency table in `cross-feature-communication.md` starts as: `topic -> dictionary` (authoring-time lookup; runtime reads the embedded snapshot, no live call), `user(progress) -> topic` (progress stores topic ids and resolves summaries via `topic.contract`), `topic x user` must never cycle — enforced, not wished.

**D7 — Guardrails before moves: ArchUnit + docs first, frozen then strict.**
Rationale: `task/refactor-plan.md` Phase 1 stands — add `archunit-junit5` (test scope), promote `task/AGENTS.md` -> root `AGENTS.md` and the other two docs -> `docs/architecture/`, add the sketched `ArchitectureTest` wrapped in `FreezingArchRule` + `archunit.properties` + committed store, `./mvnw verify` green *before* any move. Only when moves + contract shells are done and the frozen store is empty do we remove `freeze()` wrappers, delete the store/properties, and re-verify strict. Alternative (strict from day one) rejected: the tree starts with violations-by-construction during moves; freezing records them instead of blocking the refactor.

D7 amendments from the Phase-1 review (`.artifacts/feature-based-refactor/review.md`, 2026-10-16) — all three are narrowings of the guardrails, not suppressions (per AGENTS.md "no suppressions, no ArchUnit exclusions": none of them excludes classes or hides a violation). (a) F1: the frozen rules run before any `shared`/`contract` classes exist, so `shared_does_not_depend_on_features` and `contract_is_self_contained` carry a per-rule `allowEmptyShould(true)`; the reviewer proved that removing the flag makes the build fail with "failed to check any classes" while the same rules still report real violations once the packages are non-empty, and that the flag must therefore be deleted in Phase 4 so a renamed or vanished `shared`/`contract` package cannot fail open. (b) F3: nothing in `ArchitectureTest` enforces AGENTS.md hard rule 1 ("package by feature first") — the `layeredArchitecture` rule matches `..controller..`-style packages anywhere and `featureOf` accepts any first-level package, so feature-first layout is a recorded known limitation checked by the Phase 0 inventory (task 1.1) and the final Phase 4 pass (task 5.3) plus review, not by a rule. (c) F5b: Phase 4 additionally needs `.withOptionalLayers(true)` on the layered rule, because the design deliberately ships no controller/repository classes; the reviewer proved that unwrapping `freeze()` without it fails on `Layer 'Controller'`/`Layer 'Repository' is empty`, and that adding it makes the strict `verify` green.

**D8 — Move order: `shared` + `config` first, then leaf features, one feature per commit; deletions in their own commit.**
Rationale: infra first so features land on stable imports; `dictionary` before `topic` (topic references vocab ids); `user` last (references topic ids). Each step: IDE-style package move, fix wiring only from the Phase 0 grep list, `./mvnw verify` green, commit `refactor(<feature>): move to feature package`.

**D8.1 — Frozen-store re-baseline protocol (Phase 2, every move in 3.1-3.4).**

*Why this is not a suppression* (AGENTS.md: "no suppressions, no ArchUnit exclusions"): `archunit_store/` is not a rule, an exclusion, or an allow-list — it is the baseline record that Phase 1 created and Phase 4 deletes, i.e. the plan's own Phase 1→4 mechanism. `FreezingArchRule` matches violations by message *text*, and every Phase 2 package move rewrites the FQCNs embedded in those messages, so a moved class yields "new" messages for the *same dependency edge*. Regenerating the baseline on that basis excludes no class, disables no rule, and accepts no unjustified edge: the coder must first classify every failed violation at the edge level and prove each one is a moved-FQCN re-wording or the one expected new `topic.dto.response -> user.dto.response` family, and the reviewer re-checks the committed store diff against the same criterion. Anything else stops the move.

Per-move procedure (at the end of each of 3.1-3.4, before committing):

1. Run `.\mvnw.cmd -B verify` (or `-Dtest=ArchitectureTest`). If `ArchitectureTest` passes, the move is green — commit and stop here.
2. If it fails with `FreezingArchRule` "new violation(s)": capture the full failure output. For each new violation line, derive the dependency **edge** as origin class (simple name) -> target class (simple name), ignoring package prefixes, and quote it side by side with the matching committed line (`git show HEAD:src/test/resources/archunit_store/<rule-file>`, locating the rule file via `stored.rules`). Classify every failed line; message *text* is never the test:
   - ACCEPTABLE (a) — *re-wording of an already-stored edge*: the same origin class -> same target class is already stored; only moved package prefixes (and the signature text around them) differ. Record the old stored line and the new line together. Worked example (3.1): the stored line `Constructor <fit.iuh.engolearn.dto.response.TopicItemSummaryResponse.<init>(java.lang.String, java.lang.String, fit.iuh.engolearn.models.shared.enums.CefrLevel, java.lang.Integer, fit.iuh.engolearn.models.shared.enums.ContentStatus, fit.iuh.engolearn.dto.response.UserProgressSummaryResponse)> has parameter of type <fit.iuh.engolearn.models.shared.enums.ContentStatus>` returns as the identical line with only `models.shared.enums.CefrLevel` -> `shared.enums.CefrLevel`; the edge `dto.response.TopicItemSummaryResponse -> models.shared.enums.ContentStatus` is unchanged.
   - ACCEPTABLE (b) — *the one expected new family*: origin is a class D3 assigns to `topic.dto.response` (a moved `dto.response.Topic*`) and target is `UserProgressSummaryResponse` (D3: `user.dto.response`). Because 3.4 moves that class last, the family reads `topic.dto.response -> dto.response` at 3.3 (target still in the flat package) and `topic.dto.response -> user.dto.response` from 3.4 on; both spellings are Phase 0 hot spot #1 (task 1.2), not a new coupling.
   - ANY OTHER EDGE -> **STOP**. No re-baseline, no commit; leave the tree uncommitted and report to the orchestrator.
3. Only if step 2 accepted every failed line: delete the whole `src/test/resources/archunit_store/` directory (keep `archunit.properties` — Phase 4 deletes it), re-run `.\mvnw.cmd -B verify` so `allowStoreCreation=true` regenerates the store, and confirm green.
4. `git diff -- src/test/resources/archunit_store` and confirm the diff adds or removes nothing but: (i) removals of violations whose edge resolved (origin and target now in the same feature, or in `shared`/`config`/`contract`); (ii) the re-wordings accepted in 2(a); (iii) the family accepted in 2(b); (iv) the single `#<timestamp>` header line of `stored.rules`. Any other line -> STOP: restore with `git checkout -- src/test/resources/archunit_store` and report.
5. Commit the code move and the regenerated store **in the same commit**, body mentioning `re-baseline frozen store`. Never use a separate store commit, and never skip 2/4 just because the regenerated build is green.

Recurrence: the text shift is expected on **3.1** (`models.shared.enums.CefrLevel`), **3.3** (topic DTOs move; the `UserProgressSummaryResponse` edge appears) and **3.4** (`UserProgressSummaryResponse` -> `user.dto.response`, re-wording that same edge), and can occur in **3.2/3.5** if their move shifts a stored line. The gate is the same everywhere; once the store is touched, a green `verify` alone proves nothing.

Reviewer gate: for every Phase 2 commit the reviewer re-runs the criterion of steps 2 and 4 on `git show <commit> -- src/test/resources/archunit_store` and rejects any store diff containing a re-baselined edge that is not 2(a), 2(b), or a resolution.

## Risks / Trade-offs

- [Risk] `_class` drift breaks reads if anyone forgets the manual DB drop → Mitigation: proposal + tasks make the drop a named step owned by the team (outside code); `verify` runs against a fresh test DB so CI cannot mask it.
- [Risk] `shared` becomes a dumping ground for domain concepts to dodge contracts → Mitigation: D3 forces the Phase 0 enum-ownership check; `ArchitectureTest.shared_does_not_depend_on_features` + review rule "knows a feature concept => lives in that feature".
- [Risk] `TopicBrowseService` (topic content + user progress in one response shape) tempts `topic.service` to import `user.model` directly → Mitigation: D3 places progress DTO in `user`; composition happens via `user.contract` or not at all in this change — no direct import, frozen rule catches it.
- [Risk] `dictionary` rename (vocabularies -> dictionary) confuses future readers of old dumps/docs → Mitigation: collection names unchanged (`vocabularies` stays), only Java packages rename; old `task/` brief is retired, new docs use `dictionary` everywhere.
- [Risk] Deleting `learningassistant` destroys uncommitted exploration work if someone relied on those models off-branch → Mitigation: deletion is its own reviewable commit; team confirmed dev-stage acceptance.
- [Trade-off] Frozen ArchUnit temporarily hides violations during moves (could mask a new violation as "pre-existing") → accepted: freeze store is committed and diffable, and Phase 4 deletes it; alternative (no guardrails during moves) is worse.
- [Trade-off] No `internal` controllers/REST clients now means the future service split still needs that work → accepted and documented in `cross-feature-communication.md` "Future" section; building it now violates non-goals.

## Migration Plan

1. Phase 0: inventory tables (class -> target; cross-feature refs; wiring grep: `basePackages`, `@EntityScan`, `@EnableMongoRepositories`, `application*.yml` package refs, fully-qualified queries) posted for human sign-off; STOP if `topic`/`dictionary` show bidirectional runtime need (merge question) or any class ownership is unclear.
2. Phase 1: guardrails commit(s) — ArchUnit + docs + green `verify`.
3. Phase 2: ordered moves + rename + deletions, one commit each, `verify` green each time; a move whose text shift trips the frozen store follows the D8.1 re-baseline protocol and commits the regenerated store in that same commit (reviewer re-checks the store diff against D8.1); team drops dev DB by hand at an agreed point (outside CI).
4. Phase 3: minimal contract shells (`dictionary.contract.VocabularyLookup` + DTO, `topic.contract` + `user.contract` stubs as needed) + allowed-dependency table filled; no caller rewiring beyond removing the single flat import when `TopicBrowseService` moves.
5. Phase 4: unfreeze (remove `freeze()` wrappers, delete store + properties), strict `verify` green, retire `task/refactor-plan.md` (delete file), update docs table.
6. Rollback: each phase is commits that revert independently (moves are renames; guardrails-only revert is safe); post-deletion rollback of `learningassistant` is restore-from-git + manual collection restore — team accepts the gap in dev.

## Open Questions

None that change approach or tasks. Deferrable detail for Phase 0: whether `QuizType`/`CefrLevel`/`ContentStatus` in `models.shared.enums` are truly cross-feature (stay in `shared`) or topic-owned (move to `topic.model`) — answered by a usage grep during inventory without altering this design.
