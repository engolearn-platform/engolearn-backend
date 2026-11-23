# Speaking AI Slice 3 — Conversation State & Pure Conversation Manager

## Intent

Implement the deterministic domain boundary between Slice 1's
`ValidatedTurnProposal` and the later persistence/orchestration work:
`ValidatedTurnProposal + ConversationStateView + pinned snapshot -> DialogueDecision`.
The manager must be rerunnable after a state-version conflict and must never call a
provider, repository, clock, or transport.

## Goals

- Represent the authoritative state needed for one decision without exposing the
  Mongo entity to the pure manager.
- Derive completion from the session-pinned policy and accepted evidence, preserving
  `REQUIRED_GOALS_V2` and legacy ratio sessions.
- Separate `VALID`, `INVALID`, and `EVALUATION_FAILED` outcomes from the seven normal
  dialogue acts.
- Enforce answer-first, target-after-evidence, no normal `WRAP_UP`, milestone versus
  auto-end, explicit UI End, and spoken-stop semantics.
- Keep identical inputs deterministic and return nullable speech when no safe
  dialogue can be authorized.

## Non-goals

- No provider/gateway changes, WebSocket changes, FE event changes, Mongo schema,
  transaction, outbox, concurrency protocol, or runtime wiring.
- No changes to BR/UC or reviewed artifacts.
- No semantic NLP beyond the validated proposal supplied by Slice 1.

## Constraints and authority

- Business authority is BR/UC, especially BR-F1.13, F1.18, F1.20, F1.22–25,
  F1.32, F1.36–40 and BR-38.13.
- Reviewed Artifacts 01–04 lock seven acts and the
  `ValidatedTurnProposal -> DialogueDecision` boundary.
- `WRAP_UP` remains in the enum but is excluded from normal one-call decisions.
- A legacy/null completion policy keeps its pinned ratio rule; a V2 policy uses
  Required goals for completion eligibility and all goals for auto-end.

## Approach

The selected approach is a small immutable state view plus a final, stateless
manager. Completion calculations live on the state-view/policy boundary and are
delegated to by `SpeakingSessionService`, so the existing service does not keep a
second copy of the rules. The manager projects accepted evidence and one valid
turn in memory, applies lifecycle trigger precedence, then returns a decision with
outcome, reason, counters, and speech authorization; it does not mutate the input.

## Remaining builder calls

- Use existing `SpeakingSession.EndReason` only where the later persistence layer
  needs it; keep manager trigger reasons separate so Slice 3 does not expand a
  persisted contract.
- Treat omitted fixture state as an ACTIVE neutral state in tests.
- Represent service-wide unavailability as an INTERRUPTED decision with no normal
  reply; Slice 4 owns the actual transition and persistence.
