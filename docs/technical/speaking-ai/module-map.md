# Speaking AI module map

## Module overview

Speaking turns a Topic item version and published Speaking config into an independent practice session. It accepts learner audio over WebSocket, evaluates finalized turns and exposes persisted session snapshots and private playback. Follow the [runtime flows](runtime-flows.md) for ordering and the [module contract](module-contract.md) for provider and configuration boundaries.

## Component ownership map

| Responsibility | Primary component | Related components |
| --- | --- | --- |
| REST routes and WebSocket transport | [`SpeakingController`][controller], [`SpeakingSocketHandler`][socket] | [Request DTOs][requests], [response DTOs][responses], [`SpeakingWebSocketConfig`][ws-config]. The socket handler parses commands, forwards PCM and serializes events. |
| Session creation, ownership, lifecycle and persistence | [`SpeakingSessionService`][sessions] | [`SpeakingSession`][session-model], [`SpeakingContentSnapshot`][snapshot], [`SpeakingItemConfig`][item-config], [Mongo repositories][repositories]. Also owns tickets, usage, history, soft deletion and playback authorization. |
| Realtime turn and socket ownership | [`SpeakingRealtimeService`][realtime] | `SpeakingSessionService` validates the epoch and commits/settles turns. `SpeakingRealtimeService` tracks the current socket, recording and late-result delivery. |
| Streaming speech recognition | [`SpeechStreamPort`][stt-port], [`AssemblyAiSpeechStream`][stt] | Realtime owns PCM buffering, turn bounds and commit orchestration. The adapter owns STT stream state, interim text and finalization. |
| Opening generation and audio | [`SpeakingOpeningService`][opening] | `SpeakingSessionService` persists [`SpeakingOpening`][opening-model] and fences attempts. [`GeminiSpeakingOpeningGenerator`][opening-gateway] generates the reply and hint. |
| Managed turn evaluation | [`SpeakingConversationComposer`][composer] | [`PromptV2Builder`][prompt] builds the prompt. [`GeminiSpeakingAiGateway`][gateway] calls Gemini. [`SpeakingDraftValidator`][validator] validates the draft and filters evidence. |
| Deterministic dialogue and completion decisions | [`ConversationManager`][manager], [`ConversationStateView`][state] | [`DialogueDecision`][decision] carries the result. `SpeakingSessionService` applies it and owns terminal writes. |
| Assessment, turn and progress writes | [`SpeakingSessionService`][sessions] | [`SpeakingAssessmentRecordAdapter`][assessment-adapter] maps managed evidence and feedback. [`SpeakingTurn`][turn], [`SpeakingAssessmentRecord`][assessment] and `SpeakingSession` persist separate records. |
| Reply delivery, TTS and private media | [`SpeakingRealtimeService`][realtime] | [`CartesiaSpeechSynthesis`][tts] synthesizes text. [`MediaStorage`][media] stores private audio and signs URLs. `SpeakingOpeningService` coordinates opening audio separately. |

## Task locator

| Task or symptom | Start here | Related code or test |
| --- | --- | --- |
| AI repeats a question or guides toward an achieved goal | `SpeakingConversationComposer` for selected history, `PromptV2Builder` for guidance and `ConversationManager.decide` for dialogue authorization | [`ConversationManagerTest`][manager-test], [`PromptV2BuilderTest`][prompt-test] |
| Opening has weak guidance, stays pending or has no audio | `SpeakingOpeningService` and `GeminiSpeakingOpeningGenerator` | `SpeakingSessionService.retryOpening`, `SpeakingOpening`, [`SpeakingOpeningServiceTest`][opening-test] |
| Learner ends a recording and no turn commits | `SpeakingRealtimeService.end` and `SpeakingSessionService.commit` | `AssemblyAiSpeechStream.endTurn`, opening/controller checks, [`AssemblyAiSpeechStreamTest`][stt-test] |
| Assessment credits incorrect evidence | `SpeakingDraftValidator.validateV2` for managed evaluation | `SpeakingAssessmentRecordAdapter`, [`SpeakingDraftValidatorContractTest`][validator-test] |
| Goals are met but the session continues or never finishes closing | `ConversationStateView.completionFor` and `SpeakingSessionService.finalizeClosing` | `ConversationManager`, `SpeakingRealtimeService.evaluateConversation`, [`SpeakingConversationRuntimeTest`][runtime-test] |
| A reconnect stalls or an old tab still sends events | `SpeakingRealtimeService.open/close` and `SpeakingSessionService.resume/consumeTicket` | [`SpeakingConversationRuntimeTest`][runtime-test], [`SpeakingRealtimeEventShapeTest`][event-test] |
| An item cannot start Speaking, or a session uses old content | `SpeakingSessionService.create/publishedConfig` | [`SpeakingItemLookup`][topic], `SpeakingItemConfig`, `SpeakingContentSnapshot`, [`SpeakingSessionServiceTest`][session-test] |
| History or audio is missing, expired or inaccessible | `SpeakingSessionService.history/get/playback/openingPlayback` | [`SpeakingAudioAsset`][audio], `MediaStorage`, [`SpeakingSessionServiceTest`][session-test] |
| Runtime wiring differs by environment | [`SpeakingConversationConfig`][conversation-config] and shared runtime configuration | [Module contract](module-contract.md), [configuration tests][config-tests] |

## Important boundaries

- Topic supplies openable item/version facts through [`SpeakingItemLookup`][topic], implemented by [`TopicLearningServiceImpl`][topic-impl]. Speaking owns the pinned session copy and its own usage accounting. Topic retains ownership of learning progress.
- Speaking owns audio metadata in Mongo. Shared [`MediaStorage`][media] owns the storage interface and URL signing, with [`CloudinaryMediaStorage`][cloudinary] as the adapter. Speaking checks session ownership before requesting a playback URL.
- Gemini output is a draft. Validation and deterministic dialogue decisions precede settlement. Socket delivery and synthesis follow settlement on the normal response path. See the [module contract](module-contract.md#provider-and-integration-boundaries) for the managed-only path.

## Code and test navigation

- Production code lives in [`src/main/java/fit/iuh/engolearn/speaking`][source]. Start in `controller` or `service` for callers, `model` and `repository` for writes, `dto` for the public shape, and `utils/port` and `utils` for provider boundaries.
- Speaking tests live in [`src/test/java/fit/iuh/engolearn/speaking`][tests]. `service` covers opening, runtime fencing and settlement, and dialogue policy. `utils` covers prompt and adapter behavior. `model` covers completion, contract types and Mongo mappings. Configuration and security tests are under [`src/test/java/fit/iuh/engolearn/config`][config-tests].

[source]: ../../../src/main/java/fit/iuh/engolearn/speaking
[controller]: ../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingController.java
[socket]: ../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingSocketHandler.java
[requests]: ../../../src/main/java/fit/iuh/engolearn/speaking/dto/request
[responses]: ../../../src/main/java/fit/iuh/engolearn/speaking/dto/response
[sessions]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingSessionService.java
[realtime]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingRealtimeService.java
[opening]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingOpeningService.java
[composer]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingConversationComposer.java
[validator]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingDraftValidator.java
[manager]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/ConversationManager.java
[assessment-adapter]: ../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingAssessmentRecordAdapter.java
[state]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/ConversationStateView.java
[decision]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/DialogueDecision.java
[session-model]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingSession.java
[snapshot]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingContentSnapshot.java
[item-config]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingItemConfig.java
[opening-model]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingOpening.java
[turn]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingTurn.java
[assessment]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingAssessmentRecord.java
[audio]: ../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingAudioAsset.java
[repositories]: ../../../src/main/java/fit/iuh/engolearn/speaking/repository
[stt-port]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/port/SpeechStreamPort.java
[stt]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/AssemblyAiSpeechStream.java
[tts]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/CartesiaSpeechSynthesis.java
[prompt]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/PromptV2Builder.java
[gateway]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/GeminiSpeakingAiGateway.java
[opening-gateway]: ../../../src/main/java/fit/iuh/engolearn/speaking/utils/GeminiSpeakingOpeningGenerator.java
[topic]: ../../../src/main/java/fit/iuh/engolearn/topic/contract/SpeakingItemLookup.java
[topic-impl]: ../../../src/main/java/fit/iuh/engolearn/topic/service/impl/TopicLearningServiceImpl.java
[media]: ../../../src/main/java/fit/iuh/engolearn/shared/media/MediaStorage.java
[cloudinary]: ../../../src/main/java/fit/iuh/engolearn/shared/media/CloudinaryMediaStorage.java
[ws-config]: ../../../src/main/java/fit/iuh/engolearn/config/SpeakingWebSocketConfig.java
[conversation-config]: ../../../src/main/java/fit/iuh/engolearn/config/SpeakingConversationConfig.java
[tests]: ../../../src/test/java/fit/iuh/engolearn/speaking
[config-tests]: ../../../src/test/java/fit/iuh/engolearn/config
[manager-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/ConversationManagerTest.java
[prompt-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/utils/PromptV2BuilderTest.java
[opening-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingOpeningServiceTest.java
[stt-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/utils/AssemblyAiSpeechStreamTest.java
[validator-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingDraftValidatorContractTest.java
[runtime-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingConversationRuntimeTest.java
[event-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingRealtimeEventShapeTest.java
[session-test]: ../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingSessionServiceTest.java
