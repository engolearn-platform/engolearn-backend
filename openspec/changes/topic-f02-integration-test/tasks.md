## 1. Create the OpenSpec change (test-infra only, no spec delta)

- [x] 1.1 Create `proposal.md`, `design.md`, `tasks.md`, `.openspec.yaml` for `topic-f02-integration-test` with `skip_specs: true`; no `specs/` delta because no business requirement, API shape, or behavior changes. Verify: `openspec validate topic-f02-integration-test --type change --strict` → `Change 'topic-f02-integration-test' is valid`.

## 2. Migrate `pom.xml` Testcontainers 1.20.1 → BOM-managed 2.0.5

- [x] 2.1 Remove `org.testcontainers:testcontainers:1.20.1`, `org.testcontainers:mongodb:1.20.1`, `org.testcontainers:junit-jupiter:1.20.1`; add `org.testcontainers:testcontainers-mongodb` and `org.testcontainers:testcontainers-junit-jupiter` without a `<version>` (Boot 4.1.1 BOM manages 2.0.5). Nothing else in `pom.xml` changes. Verify: `.\mvnw.cmd test-compile` → compiles clean (existing `org.testcontainers.containers.MongoDBContainer` compat class still resolves).
- [x] 2.2 Prove the classpath carries only 2.0.5. Verify: `.\mvnw.cmd dependency:tree "-Dincludes=org.testcontainers"` → `testcontainers-mongodb:jar:2.0.5`, `testcontainers-junit-jupiter:jar:2.0.5`, `testcontainers:jar:2.0.5` (transitive), zero `1.20.1`.

## 3. Redesign `AbstractMongoIntegrationTest` (SENIOR — shared state, DB cleanup, conditional transaction manager)

- [x] 3.1 Replace the dead `spring.data.mongodb.*` registrations with `@DynamicPropertySource` setting `spring.mongodb.uri = mongo.getReplicaSetUrl("engolearn_it")` and `engolearn.mongodb.transactions.enabled=true`; declare the container as `org.testcontainers.mongodb.MongoDBContainer("mongo:8.0.29-noble")` with `.withReplicaSet()`; `@Testcontainers(disabledWithoutDocker = true)` on the base; `@SpringBootTest(webEnvironment = RANDOM_PORT)`. Verify: `.\mvnw.cmd test-compile` → BUILD SUCCESS.
- [x] 3.2 Add `@BeforeEach` cleanup that drops the container database `engolearn_it` through `mongoTemplate.getDb().drop()`. Verify: two consecutive runs of the IT class are both green and the log shows exactly one `mongo:8.0.29-noble` container start (shared static container, no leakage). Command: `.\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest"` twice → both runs green (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` each); full-verify log shows a single `Container mongo:8.0.29-noble ... started`.

## 4. Rewrite `TopicLearningControllerIntegrationTest` for Phase 1 (GET content)

- [x] 4.1 Remove `@Disabled`; add `@EnabledIfDockerAvailable` on the concrete class; switch to plain `org.springframework.web.client.RestTemplate` + `@LocalServerPort` and build `http://localhost:{port}/api/v1/topic-items/{itemId}/content`. Boot 4.1.1's `TestRestTemplate` needs `spring-boot-resttestclient`, which is not on this project's test classpath (`spring-boot-restclient` absent), so no dependency was added. Verify: `.\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest" "-Dlogging.level.org.mongodb.driver.protocol.command=DEBUG"` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, HTTP 200.
- [x] 4.2 Fix the fixture order with repository writes: topic → topic item → item version with id `"iv1"` → topic version whose `itemVersionIds` holds the item-version id → set `latestPublishedVersion` → save topic. Assert 200 + body content + persisted item-version title; do not assert `_id` type. Verify: same command as 4.1 → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; the green 200 proves both pointers resolve, and the `find` commands hit `topic_items` and `topic_item_versions`.
- [x] 4.3 Container proof from the driver DEBUG log: random mapped port present, server description `type=REPLICA_SET` and `PRIMARY`, zero `localhost:27017`. Verify: `ServerDescription{address=localhost:63001, type=REPLICA_SET_PRIMARY, setName='docker-rs'}` observed, `0` × `localhost:27017` in the IT log segment.

## 5. Prove the Docker-absence guard

- [x] 5.1 Point `DOCKER_HOST` at a dead endpoint and confirm the IT **skips** rather than errors, then confirm the full build stays green. Verify: `$env:DOCKER_HOST="tcp://127.0.0.1:1"; .\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest"` → skipped (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 1`); `.\mvnw.cmd verify` → `BUILD SUCCESS`; zero `Creating container for image: mongo` lines. Unset `DOCKER_HOST` afterwards.

## 6. Full verification (Phase 1 gate)

- [x] 6.1 Run the full build with Docker present and report observed numbers. Verify: `.\mvnw.cmd verify` → `BUILD SUCCESS`; `Tests run: 106, Failures: 0, Errors: 0, Skipped: 0` (baseline was `Skipped: 1`, where the single skip was this IT class), IT class `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

## 7. Phase 2 — position read/write with real transaction semantics (SENIOR, gated behind Phase 1)

- [ ] 7.1 Pending. `PUT /api/v1/topic-items/{itemId}/position` → 200, then `GET .../position` returns the same stage/index; after one `PUT` all three progress collections hold documents, proving the `@Transactional` boundary on the replica set. Owner: senior (partial-failure / transaction boundary). Verify: `.\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest"` → green plus repository count assertions.

## 8. Phase 3 — topic progress (gated behind Phase 1)

- [ ] 8.1 Pending. `GET /api/v1/topics/{topicId}/progress` → 200 with status, completed/total counts, `lastAccessedAt`, per-item progress list. Owner: coder unless the fixture requires transaction semantics, then senior. Verify: `.\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest"` → green.

## 9. Phase 4 — optimistic locking on `UserTopicItemAttempt` (SENIOR, gated behind Phase 1)

- [ ] 9.1 Pending. Saving a stale `version` raises `OptimisticLockingFailureException`; a successful save increments the version. Owner: senior (version-conflict state machine). Verify: `.\mvnw.cmd test "-Dtest=TopicLearningControllerIntegrationTest"` → green with the conflict assertion.
