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

## Local verification

Run the focused suites from the backend root with Docker and loopback available. The Gemini HTTP test uses a local fake endpoint.

```powershell
.\mvnw.cmd -ntp "-Dtest=SpeakingAssessmentMongoIntegrationTest,SpeakingAssessmentPolicyTest,SpeakingRealtimeMongoIntegrationTest,SpeakingFoundationMongoIntegrationTest,SpeakingConversationTest,SpeakingFramesTest,GeminiSpeakingGenerationTest,AssemblyAiSpeechStreamTest,CartesiaSpeechSynthesisTest,SecurityIntegrationTest,ArchitectureTest" test
```

The [runtime configuration contract](../../architecture/runtime-configuration.md) governs local and deployed inputs.
