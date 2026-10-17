## 1. Phase 0 — Inventory and sign-off (no code changes)

- [x] 1.1 Produce the class-to-target table (design D3) from the real tree and verify every `.java` file under `fit.iuh.engolearn` has exactly one target or is marked DELETE
- [x] 1.2 List every cross-feature reference after the proposed split (imports, service-to-service calls, cross-feature repository use, entity-to-entity references) and verify the list matches the known hot spots (`ItemWordVo.vocab_id/sense_id`, progress `topic_id` ids, `TopicBrowseService` composition)
- [x] 1.3 Grep wiring risks (`basePackages`, `@EntityScan`, `@EnableMongoRepositories`/`@EnableJpaRepositories`, package names in `application*.yml`, fully-qualified class names in queries) and verify the result is empty or explicitly dispositioned
- [x] 1.4 Resolve the shared-enum ownership question (`CefrLevel`, `ContentStatus`, `QuizType`: stay in `shared` vs move to `topic.model`) by usage grep and verify the decision is recorded in the inventory
- [x] 1.5 Post the Phase 0 tables for human review and verify explicit approval is recorded before any move (STOP on unclear ownership or bidirectional runtime need) — approved by user ("Duyệt"), inventory at `.artifacts/feature-based-refactor/phase0-inventory.md`

## 2. Phase 1 — Guardrails (no moves yet)

- [x] 2.1 Add `com.tngtech.archunit:archunit-junit5` (latest 1.x, test scope) to `pom.xml` and verify dependency resolves — 1.5.1, commit `1a81897`
- [x] 2.2 Promote docs (`task/AGENTS.md` -> root `AGENTS.md`, `package-structure.md` + `cross-feature-communication.md` -> `docs/architecture/`) and verify paths match the AGENTS.md references — byte-identical, commit `1a81897`
- [x] 2.3 Add `ArchitectureTest` (frozen rules per `task/refactor-plan.md`) plus `src/test/resources/archunit.properties` and the generated `archunit_store`, and verify the store is committed — store committed in `1a81897`, byte-stable across runs
- [x] 2.4 Run `./mvnw verify` and verify the build is green before any move — green (6/6 tests), reviewer re-ran independently

## 3. Phase 2 — Move code (one feature per commit, no logic changes)

Frozen-store rule for every 3.x move: once `archunit_store/` is touched, green `verify` alone is not evidence — a move that shifts a stored violation's text must follow the design D8.1 re-baseline protocol (classify every failed violation by dependency edge, else STOP), and the reviewer re-checks each move commit's store diff against D8.1.

- [x] 3.1 Move `shared` + `config` — DONE commit `19ebeb8`, D8.1 re-baseline 2(a), reviewer PASS
- [x] 3.2 Move `dictionary` — DONE commit `f4c407b`, no re-baseline needed, reviewer PASS
- [x] 3.3 Move `topic` — DONE commit `98f896a`, D8.1 re-baseline 2(b) family, reviewer PASS
- [x] 3.4 Move `user` — DONE commit `6aae0b6`, D8.1 re-baseline 2(b) re-wording, reviewer PASS
- [x] 3.5 Delete `models.learning_assistant.*` and empty dirs in a dedicated commit and verify `./mvnw verify` is green and no references to deleted packages remain — DONE commit `93be423` (14 files, 118 deletions, 0 insertions), store untouched, reviewer PASS
- [ ] 3.6 Confirm the team dropped the dev DB / stale `ai_conversations` data by hand and verify the app boots against a fresh DB

## 4. Phase 3 — Contracts (minimal shells, no caller rewiring beyond the move)

- [X] 4.1 Create `dictionary.contract.VocabularyLookup` + `contract.dto` (lookup by `vocab_id`/`sense_id`, `Optional`/contract exception, no persistence leakage) with its `service` implementation and verify `./mvnw verify` is green
- [X] 4.2 Create `topic.contract` + `user.contract` shells only as needed by the Phase 0 reference list and verify no feature imports another feature outside `contract`
- [X] 4.3 Fill the Allowed dependencies table in `cross-feature-communication.md` (`topic -> dictionary`, `user -> topic`, no cycles) and verify it matches the frozen-store findings

## 5. Phase 4 — Strict rules and cleanup

- [X] 5.1 Unfreeze the rules, narrowing first so strict rules can pass: remove the two `allowEmptyShould(true)` flags (F1 — inert once Phase 2/3 create `shared`/`contract` classes, and left in place a renamed/missing package would fail open) from `shared_does_not_depend_on_features` and `contract_is_self_contained`, and add `.withOptionalLayers(true)` to the `layers_inside_features` rule (F5b — no controller/repository classes exist by design; reviewer proved strict `verify` fails on `Layer 'Controller'`/`Layer 'Repository' is empty` without it), then remove the `FreezingArchRule.freeze(...)` wrappers, delete `archunit_store` + `archunit.properties`, and verify `./mvnw verify` is green with strict rules
- [X] 5.2 Delete/retire `task/refactor-plan.md` (and the rest of `task/` once promoted) and verify docs (`AGENTS.md`, `docs/architecture/*`, allowed-dependency table) are consistent with the final tree
- [X] 5.3 Run final `./mvnw verify` and verify green: all code under `<feature>`/`shared`/`config`, flat packages gone, `ArchitectureTest` strict and passing, behavior unchanged
