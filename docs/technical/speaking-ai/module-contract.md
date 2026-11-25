# Speaking V2 module contract

Speaking resolves learner eligibility and released context through `TopicSpeakingAccess`, reads admission through `QuotaAvailability`, records measured usage through `QuotaUsageRecording`, and writes and authorizes protected audio through `MediaManagement`. Topic owns profile drafts, publication and content access.

## Runtime configuration

Mongo indexes and scheduled recovery default to enabled through `engolearn.speaking.indexes.enabled` and `engolearn.speaking.recovery.enabled`. Tests can disable recovery to control timing.

| Property under `engolearn.speaking` | Default |
| --- | --- |
| `session.max-turns` | 30 finalized attempts |
| `session.max-duration` | 20m |
| `session.max-turn-audio` | 120s |
| `session.closing-grace` | 15s |
| `realtime.workers` | 4 provider work threads |
| `realtime.queue-capacity` | 24 waiting work or recording reservations |
| `realtime.max-connections` | 128 sockets per instance |
| `realtime.max-frames-per-second` | 160 audio frames per socket |
| `realtime.max-commands-per-second` | 16 text frames per socket |
| `azure.enabled` | true |
| `assessment.audio-workers` | 1 |
| `assessment.audio-queue-capacity` | 50 waiting jobs |
| `assessment.audio-timeout` | 20s per attempt |
| `assessment.audio-transient-retries` | 1 |
| `assessment.audio-deadline` | 30s after the session ends |
| `assessment.language-enabled` | true |
| `assessment.language-timeout` | 30s |

Allowed socket Origins come from `engolearn.cors.allowed-origins` as exact origin strings. Tickets expire after 30 seconds. Protected references are eligible for 30 days. MediaManagement signs playback URLs for up to 60 seconds.

[application.yaml](../../../src/main/resources/application.yaml) owns the AssemblyAI, Gemini, Cartesia and Azure inputs. Clients initialize lazily and require keys only when called. Configure Azure through `AZURE_SPEECH_KEY` and `AZURE_SPEECH_REGION`. Google GenAI runs through Spring AI 2.0.2 with native JSON schemas and explicit timeouts. The application allows one explicit conversation retry using the stored transcript for an eligible failed turn. Language assessment uses its own Gemini request and reuses the same externally supplied credential.

The Cartesia adapter requests raw mono 24 kHz PCM16 and writes a WAV header after receiving the audio. The header uses the completed payload size for WebSocket delivery and protected playback.

## Acoustic assessment policy

Policy `azure-v2.2` assesses eligible learner audio through the existing 120-second limit. The worker sends the full prepared PCM in one Azure SDK Continuous Recognition stream per attempt. Each learner turn has one logical acoustic sample across attempts. The recognition cap is 50 segments. The existing 20-second attempt timeout and 30-second post-session audio deadline still apply.

- **Delta to Artifact 04.** Assess the full prepared clip through 120 seconds. A single native segment keeps its Azure scores. Multiple segments retain word-weighted pronunciation and use a duration-weighted fluency estimate over valid Azure scores with reliable recognized timing. Missing fluency timing cannot supply a weight. The estimated fluency does not fully account for pauses between native segments. [Artifact 04 baseline](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/fc68b0d895c391ecd9fae85df8fa8536a7a1ebfb/docs/usecase_spec/speaking-with-AI/v2-rebuild/04-audio-assessment.md)
- **Delta to Artifact 05.** Session fluency and acoustic duration thresholds use assessed timing evidence. Session pronunciation continues to use recognized words as weights. The turn remains the unit for minimum sample counts. Missing evidence keeps a nullable score, and prior finalized results remain immutable. [Artifact 05 baseline](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/fc68b0d895c391ecd9fae85df8fa8536a7a1ebfb/docs/usecase_spec/speaking-with-AI/v2-rebuild/05-session-rubric.md)

At each aggregation level, pronunciation is `sum(score × assessed words) / sum(assessed words)`. Fluency is `sum(score × qualifying assessed duration) / sum(qualifying assessed duration)`. A valid zero contributes normally. Excluded fluency evidence contributes neither a score nor duration. The session still requires three eligible turns per acoustic criterion, with at least 20 words and 15 seconds for pronunciation and at least 20 seconds for fluency.

See the [runtime flows](runtime-flows.md#assessment) for processing order and the [P6 runbook](../../../../docs/implementation/speaking-v2-p6-runbook.md) for pending 120-second runtime verification.

## Local verification

Run the focused suites from the backend root with Docker and loopback available. The Gemini HTTP test uses a local fake endpoint.

```powershell
.\mvnw.cmd -ntp "-Dtest=SpeakingAssessmentMongoIntegrationTest,SpeakingAssessmentPolicyTest,SpeakingRealtimeMongoIntegrationTest,SpeakingFoundationMongoIntegrationTest,SpeakingConversationTest,SpeakingFramesTest,GeminiSpeakingGenerationTest,AssemblyAiSpeechStreamTest,CartesiaSpeechSynthesisTest,SecurityIntegrationTest,ArchitectureTest" test
```

The [runtime configuration contract](../../architecture/runtime-configuration.md) governs local and deployed inputs.
