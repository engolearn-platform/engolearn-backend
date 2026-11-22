# Speaking AI module contract

## Provider and integration boundaries

See the [module map](module-map.md) for component ownership and the [runtime flows](runtime-flows.md) for processing order. Speaking reads Topic through [`SpeakingItemLookup`](../../../src/main/java/fit/iuh/engolearn/topic/contract/SpeakingItemLookup.java) and uses shared [`MediaStorage`](../../../src/main/java/fit/iuh/engolearn/shared/media/MediaStorage.java) for private audio. Daily usage is accounted for inside Speaking. The current session path has no Quota or Subscription integration.

`SpeechStreamPort` and `SpeechSynthesisPort` separate STT and TTS from orchestration. AssemblyAI handles streaming recognition. Cartesia synthesizes both opening and turn replies. Opening uses the separate `SpeakingOpeningGenerator` port and Gemini adapter, independent of the manager flag.

The managed path separates prompt policy from serialized learner, content and history data. `GeminiSpeakingAiGateway` calls `generateContent` in JSON response mode with the configured model and a 12-second timeout. `SpeakingDraftValidator` validates the `speaking.ai.draft.v2` schema before producing a proposal for `ConversationManager`. V2 filters goal evidence by pinned, unique, unachieved goal IDs and requires nonblank quotes. The model judges whether those quotes support a goal. Resource evidence and corrections still have transcript checks. See [`SpeakingDraftValidatorContractTest`](../../../src/test/java/fit/iuh/engolearn/speaking/service/SpeakingDraftValidatorContractTest.java) for the distinction between V1 and V2.

Malformed provider wrappers, HTTP or transport errors, schema-invalid drafts and `UNCERTAIN` proposals produce evaluation failures. An `INVALID` proposal produces an invalid turn. The legacy `ConversationEvaluator` adapter is selected at runtime startup. Provider failures do not switch paths mid-turn.

## Runtime selection and rollback

Base [application.yaml](../../../src/main/resources/application.yaml) defaults both `engolearn.speaking.enabled` and `engolearn.speaking.manager.enabled` to true via the legacy alias placeholders `engolearn.speaking.demo.enabled` and `engolearn.speaking.demo.manager.enabled`. Explicit canonical values take precedence. [`SpeakingAvailabilityFilter`](../../../src/main/java/fit/iuh/engolearn/config/SpeakingAvailabilityFilter.java) blocks the REST/WS surface whenever `prod` is active. [`SpeakingConversationConfig`](../../../src/main/java/fit/iuh/engolearn/config/SpeakingConversationConfig.java) wires the managed composer only outside `prod` with both effective flags true.

| Effective setting | Availability gate | Evaluation selection |
| --- | --- | --- |
| `prod` profile, any flags | Blocked; requests return 404 `SPEAKING_DISABLED` | Not reachable |
| Non-prod, `speaking.enabled=false` | Blocked | Not reachable |
| Non-prod, `speaking.enabled=true`, manager flag false | Available | Legacy `GeminiConversationEvaluator` path |
| Non-prod, both effective flags true | Available | Managed `SpeakingConversationComposer` / `ConversationManager` path |

To roll back the evaluator, restart with `engolearn.speaking.manager.enabled=false`. The feature stays available if its availability flag remains enabled. No migration or reseed is needed. To close Speaking outside `prod`, set `engolearn.speaking.enabled=false`.

Other keys under `engolearn.speaking` include `gemini.model` and `gemini.endpoint` for provider calls, `indexes.enabled` for the Speaking index runner and `recovery.enabled` for startup recovery and periodic expiry. Indexes and recovery default to enabled. Request-time expiry checks still apply when the recovery runner is disabled.

## Local startup and seed data

From the backend repository root, configure the local Mongo/auth settings and `ASSEMBLYAI_API_KEY`, `GEMINI_API_KEY`, `CARTESIA_API_KEY`, `CARTESIA_VOICE_ID` and `CLOUDINARY_URL` (or separate Cloudinary credentials) through the local `.env` or process environment. See [the environment template](../../../.env.example).

```powershell
docker compose up -d mongodb
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# To try legacy evaluation, stop the running backend and restart it with:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--engolearn.speaking.manager.enabled=false"
```

The `dev` profile seeds the fixed airport fixture at item `690000000000000000000002` if missing. Open it via the FE route `/dev/speaking-session?itemId=690000000000000000000002` with an eligible learner. Separately, the [café config pack](../../../tools/mongo-json-importer/samples/speaking-ai-cafe-opening-configs.json) adds Speaking configs for already present café item versions; it does not create the Topic items. Use the [importer](../../../tools/mongo-json-importer/README.md) with that specific file and `-DryRun` before importing. `SPEAKING_DEV_SEED_FILE` is an alternative input to `SpeakingDevSeed` in `dev`: a JSON array of config records for existing item/version IDs, not the importer's collections envelope. The runner skips a blank path and existing configs.

## Accepted design and runtime limits

Session locks and tickets are local to a JVM. The [shared v0.8.2](../../../../docs/system-design/speaking-demo-contract-v0.8.2-addendum.md) and [v0.8.3](../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md) addenda define accepted dev/demo semantics for finalized turns, unresolved assessments and unavailable payloads. Durable payload recovery and aggregate CAS/outbox are outside the implementation on `dev`. See the [business mapping](../../features/speaking-with-ai/README.md) for coverage and closing gaps.
