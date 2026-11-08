# Speaking AI backend technical entry

This folder describes the backend on `feat/speaking-ai-baseline`. Read the [BE business mapping](../../features/speaking-with-ai/README.md) for coverage of SPK-UC-001–010 and gaps against the [business specification](../../../../../docs/engolearn-business-doc/docs/usecase_spec/speaking-with-AI/00-module.md). The shared [v0.8.3 addendum](../../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md) is accepted for dev/demo contract scope; its rollout remains blocked. See the [frontend handoff](../../../../speaking-ai-baseline-frontend/docs/features/speaking-with-ai/README.md) for client behavior.

| Page | Use it for |
| --- | --- |
| [REST and WebSocket contract](api-contract.md) | Routes, payload boundaries, ticket handshake, socket message types and errors |
| [Persistence and lifecycle](data-model.md) | Mongo-owned documents, learner ownership, snapshots, indexes and cleanup behavior |
| [Module flow and runtime](module-contract.md) | Component responsibilities, provider path, configuration, rollback and limitations |

## As-built summary

Speaking session REST is under `/api/v1/speaking`; the authenticated learner is taken from `CurrentUserProvider`, not a learner ID supplied by the request. Realtime audio uses `/ws/speaking/{sessionId}` after obtaining a short-lived socket ticket through REST. `SpeakingSessionService` owns session state and its Mongo records; `SpeakingRealtimeService` coordinates STT, evaluation, TTS, controller epochs and persisted turns. Topic supplies openable item facts through `SpeakingItemLookup`; Media owns private audio storage.

`SpeakingConversationComposer` orchestrates the default evaluator: `PromptV2Builder` and `GeminiSpeakingAiGateway` generate a draft, `SpeakingDraftValidator` checks it, and `ConversationManager` makes deterministic lifecycle and dialogue decisions from the validated proposal and session state. The session service settles the resulting assessment. The realtime service can project `SpeakingDialogueDecisionEvent` as the optional socket field `demoDecision`. `GeminiConversationEvaluator` is the fallback when manager mode is disabled; provider errors do not switch evaluators mid-turn.

Feature availability and manager selection are independent. Base checked-in configuration sets both canonical flags true, but the `prod` profile still blocks Speaking even if both values are true. See [runtime selection](module-contract.md#runtime-selection-and-rollback) for precedence and rollback.

The `dev` profile supplies local fixtures; [local startup and rollback](module-contract.md#local-startup-and-seed-data) describe the setup. The Vite proxy belongs to the frontend.

## Evidence and business mapping

The route and wire details below are grounded in [`SpeakingController`](../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingController.java), [`SpeakingSocketHandler`](../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingSocketHandler.java), and [`SpeakingRealtimeService`](../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingRealtimeService.java). Persistence evidence is in the linked model classes and [`SpeakingMongoIndexes`](../../../src/main/java/fit/iuh/engolearn/config/SpeakingMongoIndexes.java). Product traceability follows the use-case links above; design status is stated in the [system-design index](../../../../../docs/system-design/README.md).

The cleanup passed 254 focused tests covering Speaking, provider adapters, security and architecture. Full `mvnw.cmd -o verify` ran 692 tests: 3 failures, 5 errors, 127 skipped. The failures/errors match the pre-change baseline: Topic assertions, a missing Subscription test bean, and Windows sandbox loopback errors in app startup and VNPay. Docker was unavailable for container tests. Authenticated browser, microphone, provider and latency checks remain open.
