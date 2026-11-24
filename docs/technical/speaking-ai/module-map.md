# Speaking V2 backend module map

| Responsibility | Entry point |
| --- | --- |
| Topic profile and readiness | `TopicSpeakingProfileController`, `TopicSpeakingProfileServiceImpl`, `TopicPublishReadinessServiceImpl` |
| Learner scope gate and released content | `TopicSpeakingAccess`, `TopicLearningServiceImpl.openableScope` |
| Learner REST | `SpeakingController`, `SpeakingSessions` |
| Lease, lifecycle and durable commit | `SpeakingRuntime`, `SpeakingSessionServiceImpl` |
| WebSocket upgrade and wire adaptation | `SpeakingSocketConfiguration`, `SpeakingSocketHandshake`, `SpeakingSocketHandler`, `SpeakingFrames` |
| Opening, recording and generation orchestration | `SpeakingRealtime`, `SpeakingRealtimeImpl`, `SpeakingRecording`, `SpeakingConversation` |
| Provider adapters | `AssemblyAiSpeechStream`, `GeminiSpeakingGeneration`, `CartesiaSpeechSynthesis` |
| Mongo archives and recovery | `SpeakingTurnArchive`, `SpeakingSessionRecovery` |
| Measured usage recording and replay | `SpeakingQuota`, `SpeakingQuotaImpl` |
| Protected audio and assessment handoff | `SpeakingAudioStorage`, `SpeakingRuntime.finalizedAudioEvidence`, `FinalizedSpeakingAudio` |

`SpeakingRealtimeMongoIntegrationTest` runs actual Spring HTTP and WebSocket routes against disposable MongoDB with provider fakes. `SpeakingFoundationMongoIntegrationTest` covers Topic authoring, ownership, lifecycle fences and history. The local Gemini HTTP test checks MIME, explicit schema, raw text and retry behavior.
