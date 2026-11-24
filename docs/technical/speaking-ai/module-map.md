# Speaking V2 backend module map

| Responsibility | Entry point |
| --- | --- |
| Topic profile authoring and readiness | `TopicSpeakingProfileController`, `TopicSpeakingProfileServiceImpl`, `TopicPublishReadinessServiceImpl` |
| Learner scope gate and released content | `TopicSpeakingAccess`, `TopicLearningServiceImpl.openableScope` |
| Learner REST | `SpeakingController`, `SpeakingSessions` |
| Opening, control and turn state primitives | `SpeakingRuntime`, `SpeakingSessionServiceImpl` |
| Mongo persistence and recovery | `SpeakingSessionRepository`, `SpeakingOwnerSlotRepository`, `SpeakingTurnRepository`, `SpeakingTurnArchive`, `SpeakingSessionRecovery` |
| Protected audio playback | `SpeakingSessionServiceImpl` via `MediaManagement` |

The focused suites include `SpeakingFoundationMongoIntegrationTest`, `SecurityIntegrationTest`, `TopicLearningServiceImplTest`, `TopicPublishReadinessServiceImplTest` and `ArchitectureTest`. AssemblyAI and Cartesia adapter tests use local fixtures.
