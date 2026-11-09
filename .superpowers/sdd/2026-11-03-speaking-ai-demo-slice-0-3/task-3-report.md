# Task 3 Report — Flagged Speaking AI Demo Runtime

## Status

Implemented and committed in the backend worktree only. The legacy
`SpeakingSessionService.assessed()` path remains unchanged and remains the
fallback when the manager flag is ineffective.

## Commits

- `d361743` — `feat(speaking): wire isolated manager demo runtime`

## Implementation

- Added `SpeakingDemoManagerConfig` with
  `engolearn.speaking.demo.manager.enabled=false` as the default. The manager
  path is effective only when the existing Speaking demo gate is enabled and
  the active profile is not `prod`.
- Added `demoState` and `settleDemo` at the session service boundary. The
  settlement path re-reads session/turn state, accepts only eligible pending
  turns, persists validated evidence and manager-projected counters, handles
  invalid/evaluation-failure semantics, and fences repeated or late results.
- Added `SpeakingDemoDecisionEvent` with the required diagnostic fields.
  Flagged `TURN_RESULT` and managed evaluation-failure `PROCESSING_ERROR`
  events carry the optional diagnostic; fallback events do not.
- Wired the flagged realtime path through the existing STT, event, snapshot,
  audio, and TTS flow. TTS is gated on successful settlement, a normal
  nonblank authorized reply, and a still-current open controller.
- Updated the backend Speaking README with enablement, rollback, and
  single-instance demo limitations.

## TDD evidence

Red was observed before implementation:

```text
.\mvnw.cmd -q -Dtest=SpeakingDemoRuntimeTest test
Tests run: 6, Failures: 5, Errors: 1
```

The failures were the expected missing `SpeakingDemoManagerConfig`, demo
realtime constructor, and `settleDemo` boundary.

## Verification

- `.\mvnw.cmd -q -Dtest=SpeakingDemoRuntimeTest test` — 9/9 passed.
- `.\mvnw.cmd -q '-Dtest=SpeakingDemoRuntimeTest,SpeakingRealtimeEventShapeTest,SpeakingSessionServiceTest,SpeakingDemoGateFilterTest' test` — 20/20 passed.
- `.\mvnw.cmd verify` — build success; 309 tests passed, 0 failures, 0 errors, 14 Docker-dependent tests skipped.
- `git diff --check` — passed.
- Architecture checks passed in the final `verify` run. An earlier full run caught a temporary `config -> speaking -> config` cycle; the realtime wiring was corrected to remove the reverse dependency and the final run passed.

## Concerns and limits

- This remains a demo settlement sequence without Slice 4 transactions, CAS,
  outbox, durable resolution ledger, cross-instance fencing, or delivery
  idempotency guarantees.
- Live MongoDB/provider/browser verification was not run. Docker-dependent
  integration tests were skipped because no Docker environment was available.
- Provider credentials and real audio quality/latency remain manual demo
  concerns; no production or provider-quality claim is made.
