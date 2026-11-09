# Speaking AI Slice 3 — Conversation Manager Results

**Branch:** `feat/speaking-ai-slice-3-conversation-manager`
**Baseline:** `29cbc09` (`chore(speaking): snapshot slice 0 and slice 1 baseline`)
**Scope:** immutable conversation state view, shared completion policy, pure
`ConversationManager`, and deterministic CM fixture inventory plus pure decision tests.

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

The loader verifies metadata for all CM-01–CM-30 fixtures. Pure manager behavior
is executed against CM-01, CM-05, CM-07–CM-15, CM-17–CM-21, and CM-27, plus
dedicated regression cases for timely pending evidence after UI End, target
retention, and completion/error-counter edge cases.

| Command | Result |
|---|---|
| `./mvnw.cmd -q -Dtest=ConversationManagerTest test` | PASS; 62 cases |
| `./mvnw.cmd -q -Dtest=ConversationStateViewTest,ConversationManagerTest,SpeakingDraftValidatorContractTest,SpeakingSessionServiceTest test` | PASS; 101 cases |
| `./mvnw.cmd verify` | BUILD SUCCESS; 277 tests, 0 failures, 0 errors, 14 skipped |
| `git diff --check 29cbc09..HEAD` | PASS after committing the final patch |

The skipped tests require Docker/Testcontainers. The environment reported
`AccessDeniedException` for `\\.\pipe\docker_engine`; Spring context checks also
reported Mongo `localhost:1` `Permission denied: getsockopt`. No Docker-backed
integration evidence is claimed from this run.

## Deliberate boundaries

Slice 3 does not add persistence transactions, finalization ledgers, delivery
events, WebSocket behavior, TTS behavior, provider calls, or Slice 2 gateway
files. CM-23–CM-26 and CM-28–CM-30 require orchestration, reconciliation,
idempotency, controller, or external lifecycle state and are deferred to Slice
4/5; CM-22's pure pending-evidence decision is covered, while its commit and
delivery guarantees remain deferred.
