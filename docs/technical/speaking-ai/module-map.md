# Speaking V2 backend module map

| Responsibility | Entry point |
| --- | --- |
| Topic profile authoring and publication | `TopicSpeakingProfileController`, `TopicSpeakingProfileServiceImpl`, `TopicSpeakingResourceCatalog` |
| Learner scope gate and released content | `TopicSpeakingAccess`, `TopicLearningServiceImpl.openableScope` |
| Learner REST | `SpeakingController`, `SpeakingSessions` |
| Lease, lifecycle and durable commit | `SpeakingRuntime`, `SpeakingSessionServiceImpl` |
| WebSocket upgrade and wire adaptation | `SpeakingSocketConfiguration`, `SpeakingSocketHandshake`, `SpeakingSocketHandler`, `SpeakingFrames` |
| Opening, recording and generation orchestration | `SpeakingRealtime`, `SpeakingRealtimeImpl`, `SpeakingRecording`, `SpeakingConversation` |
| Provider adapters | `AssemblyAiSpeechStream`, `GeminiSpeakingGeneration`, `CartesiaSpeechSynthesis`, `AzureSpeakingAssessment`, `GeminiLanguageAssessment` |
| Mongo archives and recovery | `SpeakingTurnArchive`, `SpeakingSessionRecovery` |
| Measured usage recording and replay | `SpeakingQuota`, `SpeakingQuotaImpl` |
| Protected audio and assessment handoff | `SpeakingAudioStorage`, `SpeakingRuntime.finalizedAudioEvidence`, `FinalizedSpeakingAudio` |
| Acoustic jobs, scoring, and finalization | `SpeakingAssessment`, `SpeakingAssessmentRepository`, `SpeakingAcousticPolicy`, `SpeakingLanguageRubric`, `SpeakingAssessmentScheduler` |

`SpeakingRealtimeMongoIntegrationTest` runs actual Spring HTTP and WebSocket routes against disposable MongoDB with provider fakes. `SpeakingFoundationMongoIntegrationTest` covers Topic authoring, ownership, lifecycle fences and history. `SpeakingAssessmentMongoIntegrationTest` tests assessment recovery, ownership, and closing through Spring REST and disposable MongoDB. The local Gemini HTTP test checks MIME, explicit schema, raw text and retry behavior.
