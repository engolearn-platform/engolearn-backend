# Speaking AI Slice 3 — Conversation Manager Results

**Branch:** `feat/speaking-ai-slice-3-conversation-manager`  
**Baseline:** `29cbc09` (`chore(speaking): snapshot slice 0 and slice 1 baseline`)  
**Scope:** immutable conversation state view, shared completion policy, pure
`ConversationManager`, and deterministic CM fixture inventory/decision tests.

## Implemented

- `ConversationStateView` derives Required milestone, completion eligibility,
  all-goals state, and auto-end eligibility from the pinned snapshot.
- Legacy/null snapshots retain ratio completion semantics; V2 snapshots use
  Required+minimum for completion and all-goals+minimum for auto-end.
- `DialogueDecision` separates `NORMAL`, `RECOVERY`, `TERMINAL`, and `REJECT`
  from the seven `DialogueAct` values.
- `ConversationManager` is stateless and performs no database, network, clock,
  provider, or transport calls.
- `ValidatedTurnProposal` now carries a typed validity outcome and user intent,
  while retaining the Slice 1 `valid()` and constructor compatibility surface.
- Existing `SpeakingSessionService` completion helpers delegate to the shared
  policy calculation; runtime orchestration is intentionally not wired in Slice 3.

## Verification

| Command | Result |
|---|---|
| `./mvnw.cmd -q -Dtest=ConversationStateViewTest,SpeakingSessionServiceTest test` | PASS |
| `./mvnw.cmd -q -Dtest=ConversationManagerTest,SpeakingDraftValidatorContractTest,ConversationStateViewTest,SpeakingSessionServiceTest test` | PASS; manager test reports 40 cases, validator contract 28 cases |
| `./mvnw.cmd verify` | BUILD SUCCESS; 254 tests, 0 failures, 0 errors, 14 skipped |
| `git diff --check` | PASS |

The skipped tests require Docker/Testcontainers. The environment reported
`AccessDeniedException` for `\\.\pipe\docker_engine`; Spring context checks also
reported Mongo `localhost:1` `Permission denied: getsockopt`. No Docker-backed
integration evidence is claimed from this run.

## Deliberate boundaries

Slice 3 does not add persistence transactions, finalization ledgers, delivery
events, WebSocket behavior, TTS behavior, provider calls, or Slice 2 gateway
files. Those remain Slice 4/5 work.
