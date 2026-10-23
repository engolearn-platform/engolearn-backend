## Why

`TopicLearningControllerIntegrationTest` is `@Disabled` and has never actually exercised a MongoDB container. Two independent causes:

1. Test wiring: `AbstractMongoIntegrationTest` published the container URI with `spring.data.mongodb.*` keys. Spring Boot 4.1 marks `spring.data.mongodb.uri` deprecated at error level; the replacement is `spring.mongodb.uri`. `src/main/resources/application.yaml` sets `spring.mongodb.host/port/...`, which wins over the dead key, so the test connected to the developer's `mongodb_srv:27017` (standalone, auth enabled) while the Testcontainers container sat idle.
2. Fixture: the seed data did not set `Topic.latestPublishedVersion`, and the version manifest stored topic-item ids instead of item-version ids. Either omission alone yields `404`, so the test could not pass even against a healthy container.

The 404 was misdiagnosed for a while as an `_id` `String`/`ObjectId` mapping problem. Wiring the test to a real container with the correct Boot 4 configuration is what stops that misdiagnosis from recurring.

The pinned Testcontainers version is also unsupported: `pom.xml` declares `org.testcontainers:{testcontainers,mongodb,junit-jupiter}:1.20.1`, a combination that is not compatible with Spring Boot 4.1.1. The Boot 4.1.1 BOM manages Testcontainers 2.0.5, where those artifacts are named `testcontainers-mongodb` and `testcontainers-junit-jupiter`.

## What Changes

- Redesign `AbstractMongoIntegrationTest`: container typed `org.testcontainers.mongodb.MongoDBContainer` (`mongo:8.0.29-noble`) with `.withReplicaSet()` called explicitly; `@SpringBootTest(webEnvironment = RANDOM_PORT)`; `@DynamicPropertySource` registers `spring.mongodb.uri = mongo.getReplicaSetUrl("engolearn_it")` and `engolearn.mongodb.transactions.enabled=true`; the dead `spring.data.mongodb.*` registrations are removed.
- Docker-absence guard: `@EnabledIfDockerAvailable` on the concrete test class (it is not `@Inherited`) plus `@Testcontainers(disabledWithoutDocker = true)` on the base (`@Inherited`). `getReplicaSetUrl(...)` must never be called when Docker is unavailable.
- DB isolation: `@BeforeEach` drops the container database `engolearn_it` only, so the static shared container does not leak fixture state between test methods or classes.
- HTTP client: plain `org.springframework.web.client.RestTemplate` + `@LocalServerPort` instead of a hardcoded `http://localhost:8080`. Boot 4.1.1's `TestRestTemplate` lives in `spring-boot-resttestclient`, and its `spring-boot-restclient` dependency is absent from this project's test classpath (`NoClassDefFoundError: org/springframework/boot/restclient/RestTemplateBuilder`); no dependency was added.
- Fixture fix: repository-based writes in the proven `DevTopicSeedRunner` order — topic, topic item, item version with a repository-generated 24-hex id, topic version whose `itemVersionIds` holds the item-version id, then `latestPublishedVersion` set and the topic saved again. The id must not be pinned to a business string: the progress models map `topicItemVersionId` with `@Field(targetType = OBJECT_ID)`, so a non-hex id makes `PUT /position` fail with `ConversionFailedException` (500).
- Remove `@Disabled` so the integration test runs inside the default `mvnw verify`.
- `pom.xml` dependency migration (owner approved 2026-10-23): remove `org.testcontainers:testcontainers:1.20.1`, `org.testcontainers:mongodb:1.20.1`, `org.testcontainers:junit-jupiter:1.20.1`; add BOM-managed `org.testcontainers:testcontainers-mongodb` and `org.testcontainers:testcontainers-junit-jupiter` (no `<version>`; the Spring Boot 4.1.1 BOM resolves 2.0.5). Reason: the 1.20.1-vs-Boot-4.1.1 combination is unsupported and Testcontainers 2.x flattened the artifact coordinates. Core `testcontainers:2.0.5` arrives transitively.

## Capabilities

### New Capabilities

- Phase 1: integration coverage for `GET /api/v1/topic-items/{itemId}/content` against a real MongoDB replica-set container, including a Docker-absent skip path.
- Phase 2–4 (pending, gated behind a green Phase 1): position read/write with real transaction semantics, topic progress, and optimistic locking on `UserTopicItemAttempt`.

### Modified Capabilities

- (none)

## Impact

- Test files only: `src/test/java/fit/iuh/engolearn/config/AbstractMongoIntegrationTest.java`, `src/test/java/fit/iuh/engolearn/topic/controller/TopicLearningControllerIntegrationTest.java`.
- Dependency change: the approved Testcontainers 1.20.1 → 2.0.5 BOM-managed migration in `pom.xml` (`testcontainers`, `mongodb`, `junit-jupiter` → `testcontainers-mongodb`, `testcontainers-junit-jupiter`, versions removed). Old→new coordinates are recorded above; approval date 2026-10-23. No other dependency is added.
- No production code, no API shape, no business behavior, no `src/main/` change; `src/main/resources/application.yaml` stays untouched.
- Test-infra only: no new or changed business requirement, therefore `skip_specs: true` and no `specs/` delta. `openspec/specs/topic-learn-item/spec.md` already covers all four endpoints.
- Deferred to a separate change needing its own approval: `org.springframework.boot:spring-boot-testcontainers` and `@ServiceConnection` (that module is not on the test classpath today).
