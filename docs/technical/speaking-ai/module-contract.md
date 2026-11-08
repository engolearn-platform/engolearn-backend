# Speaking AI module flow and runtime

## Module split

| Package/component | Responsibility |
| --- | --- |
| `speaking.controller`, request/response DTOs | Authenticated REST adaptation and WebSocket transport; socket handler does protocol parsing only. |
| `SpeakingSessionService` | Learner ownership, item/config lookup, session lifecycle, ticket issue/consume, persistence, playback and ownership checks. |
| `SpeakingRealtimeService` | STT stream and turn coordination, controller fencing, turn lifecycle, provider calls, persistence settlement and socket events. |
| `SpeakingConversationComposer` | Builds the managed AI request, invokes the gateway, validates the provider draft and delegates deterministic interpretation. |
| `SpeakingConversationAssessment`, `SpeakingDialogueDecisionEvent`, `ConversationManager` | Typed assessment/decision/event values and deterministic dialogue/lifecycle decision rules. |
| `PromptV2Builder`, `GeminiSpeakingAiGateway` | Prompt V2 construction and Gemini HTTP provider adapter for managed drafts. |
| `GeminiConversationEvaluator` | Legacy evaluator path when conversation manager selection is off; it remains available for rollback. |
| `speaking.repository`, `speaking.model` | Speaking-owned Mongo data and persistence ports. |
| `SpeakingItemLookup`, `MediaStorage`, STT/TTS/evaluator ports | Boundaries to Topic, Media and provider adapters. |

Conversation flow: an authenticated learner opens or resumes a session, requests its socket ticket, then streams bounded PCM. AssemblyAI sends interim text; the backend commits a nonblank final transcript before evaluation. In manager mode, `SpeakingDraftValidator` and `ConversationManager` produce the proposal and dialogue decision; `SpeakingSessionService.settleConversationTurn` checks session, epoch and pending state before saving. Legacy mode calls `GeminiConversationEvaluator` and settles through `assessed`. A successful result snapshot precedes any Cartesia reply audio; audio metadata is saved separately. The authoritative session snapshot lets the client recover after disconnect or controller replacement.

Opening generation is separate from per-turn evaluation. `SpeakingOpeningService` uses `GeminiSpeakingOpeningGenerator` and Cartesia; the manager flag does not affect it. A failed opening can be retried, and synthesis-only failure keeps the generated text available. Opening states are persisted on the session and do not count as turns.

The managed Prompt V2 path labels backend policy separately from serialized learner/content/history data. The prompt asks Gemini for structured JSON; `GeminiSpeakingAiGateway` calls `generateContent` with JSON response mode and the configured API key, model, and a 12 second timeout. Malformed provider wrappers, HTTP failures, transport errors and schema-invalid drafts become explicit evaluation outcomes. There is no automatic per-turn fallback to another provider. `GeminiConversationEvaluator` is the legacy selection path when manager mode is disabled.

## Runtime selection and rollback

Base checked-in `application.yaml` currently enables both `engolearn.speaking.enabled` and `engolearn.speaking.manager.enabled`. The canonical key takes precedence when present; legacy `engolearn.speaking.demo.enabled` and `engolearn.speaking.demo.manager.enabled` are consulted only when their canonical counterpart is absent. The `prod` profile blocks the REST/WS surface regardless of flag values.

| Effective setting | Availability gate | Evaluation selection |
| --- | --- | --- |
| `prod` profile, any flags | Blocked; requests return 404 `SPEAKING_DISABLED` | Not reachable |
| Non-prod, `speaking.enabled=false` | Blocked | Not reachable |
| Non-prod, `speaking.enabled=true`, manager flag false | Available | Legacy `GeminiConversationEvaluator` path |
| Non-prod, both effective flags true | Available | Managed `SpeakingConversationComposer` / `ConversationManager` path |

To roll back the evaluator, restart with `engolearn.speaking.manager.enabled=false`. The feature stays available if its availability flag remains enabled. No migration or reseed is needed. To close Speaking outside `prod`, set `engolearn.speaking.enabled=false`.

## Local startup and seed data

From this BE worktree, configure the local Mongo/auth settings and `ASSEMBLYAI_API_KEY`, `GEMINI_API_KEY`, `CARTESIA_API_KEY`, `CARTESIA_VOICE_ID` and `CLOUDINARY_URL` (or separate Cloudinary credentials) through the local `.env` or process environment. See [the environment template](../../../.env.example).

```powershell
docker compose up -d mongodb
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# To try legacy evaluation, stop the running backend and restart it with:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--engolearn.speaking.manager.enabled=false"
```

The `dev` profile seeds the fixed airport fixture at item `690000000000000000000002` if missing. Open it via the FE route `/dev/speaking-session?itemId=690000000000000000000002` with an eligible learner. Separately, the [café config pack](../../../tools/mongo-json-importer/samples/speaking-ai-cafe-opening-configs.json) adds Speaking configs for already present café item versions; it does not create the Topic items. Use the [importer](../../../tools/mongo-json-importer/README.md) with that specific file and `-DryRun` before importing. `SPEAKING_DEV_SEED_FILE` is an alternative input to `SpeakingDevSeed` in `dev`: a JSON array of config records for existing item/version IDs, not the importer's collections envelope. The runner skips a blank path and existing configs.

Other runtime keys under `engolearn.speaking`: `gemini.model`, `gemini.endpoint`, `indexes.enabled` and `recovery.enabled`. Provider settings are defined in [application.yaml](../../../src/main/resources/application.yaml) and consumed by the adapters.

## Limitations and evidence status

* `GeminiConversationEvaluator` remains legacy; managed path uses `GeminiSpeakingAiGateway` and does not automatically fail over on a provider error.
* Gemini prompt evidence quotes are checked structurally but semantic interpretation/provenance remains model-dependent as described in the prompt. A valid JSON response is not a guarantee of learning quality.
* No production availability is implied: production profile blocks Speaking even with flags enabled.
* Session locks and tickets are local to a JVM. The manager path does not implement the v0.8.3 durable payload/blob or aggregate CAS/outbox plan. See the [business mapping](../../features/speaking-with-ai/README.md#đối-chiếu-luật-f01f03) for closing and timeout gaps.
* The `demoDecision` socket property is optional compatibility data. The `X-Speaking-Contract` header and existing external identifiers remain unchanged.

See the [business mapping](../../features/speaking-with-ai/README.md) and [shared v0.8.3 addendum](../../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md) for product requirements and rollout status.
