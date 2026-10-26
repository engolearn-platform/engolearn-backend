## Context

`TopicLearningControllerIntegrationTest` and its base class `AbstractMongoIntegrationTest` exist but have been `@Disabled` since the learn-item change. The current base class registers the container URL under `spring.data.mongodb.*`. Spring Boot 4.1 deprecates `spring.data.mongodb.uri` at error level (replacement: `spring.mongodb.uri`), and `application.yaml` sets `spring.mongodb.host/port/...`, so the application context always resolved the developer's `mongodb_srv:27017` instead of the container. The fixture was also incomplete: `Topic.latestPublishedVersion` was never set and the version manifest stored topic-item ids rather than item-version ids, so the content lookup returned `404` for two independent reasons.

`pom.xml` additionally pins Testcontainers 1.20.1 with coordinates that no longer exist in 2.x. The Spring Boot 4.1.1 BOM manages Testcontainers 2.0.5 (`testcontainers-mongodb`, `testcontainers-junit-jupiter`), and the locally cached 2.0.5 jars are what the redesign is written against.

## Goals / Non-Goals

**Goals:**

- The Phase 1 integration test runs against a real MongoDB replica-set container, not `localhost:27017`.
- Correct Spring Boot 4.1 property key (`spring.mongodb.uri`) with a dedicated IT database `engolearn_it`.
- The test skips cleanly (not fails) when Docker is unavailable, so the build stays green on CI.
- A transaction manager exists in the IT context (`engolearn.mongodb.transactions.enabled=true`) so the gated Phase 2+ `@Transactional` work is possible without further infra changes.
- Isolated tests: no fixture leakage through the shared static container.
- Testcontainers dependencies become BOM-managed 2.0.5; no other dependency added.

**Non-Goals:**

- `@ServiceConnection` / `org.springframework.boot:spring-boot-testcontainers`. That module is not on the test classpath and adding it is a new dependency requiring separate owner approval.
- Any production code change (`src/main/`), `application.yaml`, `MockCurrentUserProvider`, `TopicItem.topicId` typing, or `@Version` on `UserTopicItemAttempt`.
- Phases 2–4 of the integration suite (position, progress, optimistic locking). They stay in this change but are gated behind a green Phase 1 review.
- A `specs/` delta: this is test infrastructure, no business requirement or API behavior changes.

## Quyết định

### D1: `@DynamicPropertySource` with the Boot 4 key, container from `org.testcontainers.mongodb`

Register `spring.mongodb.uri = mongo.getReplicaSetUrl("engolearn_it")`. This beats the app's `spring.mongodb.host/port/...` and removes the root cause (dead key). The database name is `engolearn_it`, never the dev `engolearn_db`. `@ServiceConnection` is rejected for now because the required Boot module is not on the classpath (see Non-Goals).

### D2: Docker-absence guard on two layers

`@EnabledIfDockerAvailable` on **every** concrete integration class: the annotation is not `@Inherited`, and it is a JUnit `ExecutionCondition`, so it gates before the Spring context loads and before `beforeAll`. The second layer is the Docker check in the base-class static initializer, which starts the singleton container only when `DockerClientFactory.instance().isDockerAvailable()`. Together they guarantee `getReplicaSetUrl(...)` is never called with no started container: a skipped class never starts a context, so the lazy `@DynamicPropertySource` supplier is never evaluated. The base class no longer uses `@Testcontainers`/`@Container`.

### D3: `RANDOM_PORT` + plain `RestTemplate` + `@LocalServerPort`

Removes the hardcoded `http://localhost:8080` and the collision risk with a running dev process. `server.port: 8080` in `application.yaml` is overridden only at test runtime. The client is plain `org.springframework.web.client.RestTemplate` because Boot 4.1.1's `TestRestTemplate` needs `spring-boot-resttestclient`, which is not on the test classpath, and no dependency was added.

### D4: `.withReplicaSet()` explicitly; no `--replSet`, no `rs.initiate()`, no `waitingFor`

In Testcontainers 2.0.5 replica-set mode is not automatic: `org.testcontainers.mongodb.MongoDBContainer#withReplicaSet()` must be called, otherwise the container comes up standalone. A standalone container breaks `@Transactional` in Phase 2, so Phase 1 evidence must show `type=REPLICA_SET` / `PRIMARY` in the driver log before Phase 2 starts. Passing `--replSet` manually or calling `rs.initiate()` in `execInContainer` duplicates what the image already does.

### D5: `engolearn.mongodb.transactions.enabled=true` from the same property source

`MongoTransactionConfig.mongoTransactionManager` is `@ConditionalOnProperty(prefix="engolearn.mongodb.transactions", name="enabled", havingValue="true")`. Without it, `saveItemPosition` fails with no transaction manager. The replica-set container supports transactions, unlike the standalone dev server.

### D6: Drop `engolearn_it` before each test

The singleton container is started once per JVM and shared by every test class, so fixtures leak without cleanup. `mongoTemplate.getDb().drop()` runs before each test against the container database only (no auth in the container, no chance of touching `mongodb_srv:27017`). The JVM-wide singleton is also what keeps one Spring context valid across classes: the context is never re-pointed at a container that has stopped.

### D7: Testcontainers 1.20.1 → BOM-managed 2.0.5

Remove the three pinned test dependencies and add `org.testcontainers:testcontainers-mongodb` plus `org.testcontainers:testcontainers-junit-jupiter` without a version. Core `testcontainers:2.0.5` comes transitively. The migration is owner-approved (2026-10-23) because 1.20.1 does not support Spring Boot 4.1.1 and the 2.x coordinates are different. Nothing else in `pom.xml` changes.

### D8: Fixture writes through repositories, in the seed-runner order

Order: topic → topic item → item version with a repository-generated id → topic version whose `itemVersionIds` holds the item-version id → `topic.setLatestPublishedVersion(release.getId())` → save topic again. This matches `DevTopicSeedRunner`, which never sets `TopicItemVersion.id`, so production ids are the repository-generated 24-hex strings that fit the `@Field(targetType = OBJECT_ID)` mapping on `topicItemVersionId`. Pinning a non-hex business id makes `PUT /position` fail with `ConversionFailedException` (500). Raw `Document` writes are avoided so the fixture exercises the same mapping as production code.

### D9: OpenSpec change before any test code

This change is created first, with `skip_specs: true` and no `specs/` delta, because it changes no business requirement, API shape, or behavior. The dependency migration and its approval trail are recorded in the proposal. `@ServiceConnection` remains a separate, separately-approved change.

### D10: Production code untouched

No `src/main/` edit. The hardcoded mock user in `MockCurrentUserProvider` is an auth concern for a later change; `TopicItem.topicId` typing and `@Version` on `UserTopicItemAttempt` are out of scope here.

## Rủi ro / Trade-offs

- [Risk] Testcontainers 2.0.5 does not auto-initiate the replica set; forgetting `.withReplicaSet()` yields `type=STANDALONE` and breaks Phase 2 transactions. → Mitigation: Phase 1's evidence gate requires `REPLICA_SET`/`PRIMARY` in the driver log before Phase 2 begins.
- [Risk] A base-class `@DynamicPropertySource` might not reach the subclass context, leaving `spring.mongodb.uri` or the transaction flag unset. → Mitigation: if observed, move the property source to the concrete class (Phase 2 verification catches it).
- [Risk] Guard ordering: with Docker absent, a context that still tries `localhost:27017` could error instead of skip on a machine without a local mongod. → Mitigation: `@EnabledIfDockerAvailable` is an `ExecutionCondition` gating before `beforeAll`; proven with `DOCKER_HOST=tcp://127.0.0.1:1`.
- [Risk] `getReplicaSetUrl` throws when called before container start with Docker absent. → Mitigation: the static initializer only starts the container when Docker is available (so a started container exists whenever a context can load), and `@EnabledIfDockerAvailable` on each concrete class skips the class before the context — and therefore the lazy supplier — is evaluated.
- [Risk] A static `@Container` on an abstract base is per-class, and the Spring TestContext cache key ignores `@DynamicPropertySource` values. → The second class then reuses the first class's cached context, which points at the stopped container's old port, and the `@BeforeEach` drop fails with `MongoTimeoutException`. → Mitigation: the current singleton design (one container per JVM, never stopped) plus a both-orders check in Phase 4 so each class leads once; not `@DirtiesContext`, which restarts contexts but leaves the per-class container start/stop in place.
- [Risk] `db.drop()` fails if the container later enables auth. → Accepted: the container runs without auth today (no init script); revisit if auth is added.
- [Risk] Docker image pull failure. → Accepted: `mongo:8.0.29-noble` is already local; fail fast instead of retrying in a loop.
- [Risk] The 2.0.5 migration moves imports to flattened packages. → Mitigation: `test-compile` after the pom change; `testcontainers:2.0.5` and no `1.20.1` confirmed via `dependency:tree`.
- [Limitation — Phase 2] Rollback on a mid-operation failure is **not asserted**: there is no production seam to force a failure between the four writes, and the only `abortTransaction` observation came incidentally from a pre-fix failing run. Phase 2 proves `startTransaction` → four inserts → `commitTransaction`; rollback remains unproven.

## Migration Plan

1. `pom.xml`: replace the three pinned 1.20.1 test dependencies with BOM-managed `testcontainers-mongodb` and `testcontainers-junit-jupiter`. Old→new coordinates and the 2026-10-23 approval are recorded in `proposal.md`. No other pom change.
2. Verify the dependency graph before touching test code, so the build never sits on a mixed 1.20.1/2.0.5 classpath.
3. Redesign the base class around one JVM-wide container (started in a static initializer, never stopped), then rewrite the Phase 1 test.
4. `@ServiceConnection` and `spring-boot-testcontainers` stay deferred: separate change, separate approval.

## Câu hỏi còn mở

- (không có)
