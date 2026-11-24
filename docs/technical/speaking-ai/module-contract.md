# Speaking V2 module contract

## Integration

Speaking resolves eligibility and released context through `TopicSpeakingAccess`, checks quota through `QuotaAvailability`, and authorizes protected playback before invoking `MediaManagement`. Topic owns profile drafts and promotion. The [runtime flows](runtime-flows.md) identify P1 behavior and the P2 and P3 seams.

## Runtime configuration

Both application profiles use the same Speaking foundation. Mongo index initialization and periodic recovery default to enabled via `engolearn.speaking.indexes.enabled` and `engolearn.speaking.recovery.enabled`. Focused tests can disable recovery to control timing.

| Property under `engolearn.speaking.session` | Default |
| --- | --- |
| `max-turns` | 30 finalized attempts |
| `max-duration` | 20m |
| `max-turn-audio` | 120s |
| `closing-grace` | 15s |

Socket tickets expire after 30 seconds. Private media references are eligible for 30 days. Shared Media issues signed playback URLs with a lifetime of up to 60 seconds.

[application.yaml](../../../src/main/resources/application.yaml) owns provider credentials and defaults. AssemblyAI and Cartesia adapters are retained with their focused tests. Their credential and endpoint values are available through the common configuration and require no provider connection at application startup. P2 owns the provider dispatch and Gemini configuration integration.

## Local verification

Run focused checks against disposable containers from the backend root.

```powershell
.\mvnw.cmd -ntp "-Dtest=SpeakingFoundationMongoIntegrationTest,TopicLearningServiceImplTest,TopicPublishReadinessServiceImplTest,SecurityIntegrationTest,ArchitectureTest" test
```

The [runtime configuration contract](../../architecture/runtime-configuration.md) governs local and deployed inputs. Profile authoring routes can prepare released sample Topic content. Legacy Speaking seed collections are retired and V2 startup leaves existing data untouched.
