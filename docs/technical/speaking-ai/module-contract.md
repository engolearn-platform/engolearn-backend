# Speaking AI module contract

## Provider and integration boundaries

See the [module map](module-map.md) for component ownership and the [runtime flows](runtime-flows.md) for processing order. Speaking reads Topic through [`SpeakingItemLookup`](../../../src/main/java/fit/iuh/engolearn/topic/contract/SpeakingItemLookup.java) and stores private audio through shared [`MediaStorage`](../../../src/main/java/fit/iuh/engolearn/shared/media/MediaStorage.java). Cloudinary currently stores media through its authenticated private-storage adapter. Speaking persists the provider public ID and format with its audio metadata. Media ID resolution through `MediaManagement` is not yet integrated. Daily usage is accounted for inside Speaking. The current session path has no Quota or Subscription integration.

`SpeechStreamPort` and `SpeechSynthesisPort` separate STT and TTS from orchestration. AssemblyAI handles streaming recognition. Cartesia synthesizes both opening and turn replies. Opening uses the separate `SpeakingOpeningGenerator` port and Gemini adapter. Conversation evaluation follows `SpeakingConversationComposer` → `GeminiSpeakingAiGateway` → `SpeakingDraftValidator` → `ConversationManager`.

The gateway calls Gemini's `generateContent` API in JSON response mode using the configured model. The `SpeakingDraftValidator` validates the `speaking.ai.draft.v2` schema before producing a proposal for `ConversationManager`. V2 filters goal evidence by pinned, unique, unachieved goal IDs and requires nonblank quotes. The model judges whether those quotes support a goal. Resource evidence and corrections still have transcript checks. See [`SpeakingDraftValidatorContractTest`](../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingDraftValidatorContractTest.java) for the distinction between V1 and V2.

Malformed provider wrappers, HTTP or transport errors, schema-invalid drafts and `UNCERTAIN` proposals produce evaluation failures. An `INVALID` proposal produces an invalid turn. Provider failures remain explicit evaluation failures and do not select another evaluator or mock provider. API keys and tokens must not be logged.

## Runtime configuration

Speaking is a normal business module in both supported application profiles, `dev` and `prod`. The managed conversation beans are registered in both. There is no Speaking-specific runtime profile, evaluator-selection switch or availability gate. Authentication, ownership checks and the ticket-authenticated WebSocket handshake continue to govern access.

The shared [application.yaml](../../../src/main/resources/application.yaml) owns the provider defaults. Each property below starts with `engolearn.speaking.` and applies in both runtime profiles. Each API key defaults to empty and is required when its capability is called.

| Property | Environment input | Default |
| --- | --- | --- |
| `assemblyai.api-key` | `ASSEMBLYAI_API_KEY` | Empty |
| `assemblyai.endpoint` | `SPEAKING_ASSEMBLYAI_ENDPOINT` | `wss://streaming.assemblyai.com/v3/ws` |
| `assemblyai.model` | `SPEAKING_ASSEMBLYAI_MODEL` | `universal-3-6-pro` |
| `assemblyai.connect-timeout` | `SPEAKING_ASSEMBLYAI_CONNECT_TIMEOUT` | 10s |
| `assemblyai.finalization-timeout` | `SPEAKING_ASSEMBLYAI_FINALIZATION_TIMEOUT` | 6s |
| `gemini.api-key` | `GEMINI_API_KEY` | Empty |
| `gemini.endpoint` | `SPEAKING_GEMINI_ENDPOINT` | `https://generativelanguage.googleapis.com/v1beta/models` |
| `gemini.model` | `SPEAKING_GEMINI_MODEL` | `gemini-3.5-flash-lite` |
| `gemini.conversation-timeout` | `SPEAKING_GEMINI_CONVERSATION_TIMEOUT` | 12s |
| `gemini.opening-timeout` | `SPEAKING_GEMINI_OPENING_TIMEOUT` | 10s |
| `cartesia.api-key` | `CARTESIA_API_KEY` | Empty |
| `cartesia.endpoint` | `SPEAKING_CARTESIA_ENDPOINT` | `https://api.cartesia.ai/tts/bytes` |
| `cartesia.model` | `SPEAKING_CARTESIA_MODEL` | `sonic-3.6` |
| `cartesia.voice` | `CARTESIA_VOICE_ID` | `db6b0ed5-d5d3-463d-ae85-518a07d3c2b4` |
| `cartesia.api-version` | `SPEAKING_CARTESIA_API_VERSION` | `2026-08-14` |
| `cartesia.timeout` | `SPEAKING_CARTESIA_TIMEOUT` | 20s |
| `indexes.enabled` | `ENGOLEARN_SPEAKING_INDEXES_ENABLED` | true |
| `recovery.enabled` | `ENGOLEARN_SPEAKING_RECOVERY_ENABLED` | true |

The opening service also limits the entire generation stage to 10s and the TTS plus storage stage to 20s. Request-time expiry checks still apply when the startup recovery and expiry runner is disabled.

Private upload and signed playback require shared `engolearn.media.cloudinary.url` (`CLOUDINARY_URL`) or all three of `cloud-name`, `api-key` and `api-secret` under the same prefix (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET`). These inputs default to empty. The storage bean reports missing credentials on the first storage operation.

## Local startup and content import

From the backend repository root, configure local Mongo/auth settings and the provider credentials required by the capability being exercised through `.env` or process environment. See [the environment template](../../../.env.example).

```powershell
docker compose up -d mongodb
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The [café config pack](../../../tools/mongo-json-importer/samples/speaking-ai-cafe-opening-configs.json) adds Speaking configs for already present café item versions; it does not create Topic items. Use the [importer](../../../tools/mongo-json-importer/README.md) with that specific file and `-DryRun` before importing.

## Verification

From the backend root, run the focused provider, lifecycle, profile and security checks, then the change-level build. Docker enables the database-backed suites.

```powershell
.\mvnw.cmd "-Dtest=*Speaking*,Conversation*,*Speech*,Gemini*,PromptV2BuilderTest,RuntimeProfilesTest,SecurityIntegrationTest" test
.\mvnw.cmd verify
```

## Accepted design and runtime limits

Session locks and tickets are local to a JVM. The [shared v0.8.2](../../../../docs/system-design/speaking-demo-contract-v0.8.2-addendum.md) and [v0.8.3](../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md) addenda preserve accepted historical design and dev/demo semantics. They do not control the current evaluator path. Durable payload recovery and aggregate CAS/outbox remain unimplemented. Multi-instance coordination is not available. Shared Media integration is incomplete because Speaking still stores Cloudinary public IDs and format rather than resolving persisted media IDs through `MediaManagement`. Real provider, database, storage, authorization and multi-instance smoke tests remain necessary before production readiness can be claimed. See the [business mapping](../../features/speaking-with-ai/README.md) for coverage and lifecycle gaps.
