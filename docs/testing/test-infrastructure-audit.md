# Test infrastructure audit and verification workflow

## Scope and baseline

The backend is a single Spring Boot 4.1.1 Maven module using Java 21 in the
build configuration. The execution environment used for this audit reported
Java 22.0.1 at test runtime. Spring Boot manages JUnit Jupiter 6.0.3,
Mockito 5.23.0, Byte Buddy 1.18.11, Surefire 3.5.6, and Testcontainers 2.0.5.
No JaCoCo plugin or Failsafe configuration was present before this change.

The tests fall into these groups:

- Unit tests: service, mapper, model, utility, security-provider, and
  concurrency tests that use fakes or Mockito and do not need MongoDB.
- Component tests: `@WebMvcTest` tests, including `SecurityIntegrationTest`.
  The name contains `Integration`, but it is a web slice test and does not
  need Docker.
- Mongo integration tests: Testcontainers Mongo tests and standalone Mongo
  tests under `*IntegrationTest`, including the shared replica-set base and
  LearningPath standalone containers.
- External-service adapter tests: AssemblyAI uses a mocked `HttpClient` and
  Cartesia now uses a mocked `HttpClient`; neither test requires credentials or
  network access.

The integration tests require a Docker daemon with access to the Docker named
pipe. Some application context tests intentionally point MongoDB at
`localhost:1`; that is a closed port used to prove startup guards, not a
production connectivity check. No test in the audited set requires live
AssemblyAI, Cartesia, Gemini, or Cloudinary credentials.

## Root cause: Mockito agent warning

Mockito inline mocking was self-attaching Byte Buddy dynamically. The baseline
run passed, but emitted the JDK warning that dynamic agent loading will be
disallowed in a future JDK. This was a test JVM configuration issue, not a
Mockito assertion failure.

The fix is a test-scoped `net.bytebuddy:byte-buddy-agent` dependency managed by
Spring Boot and a static `-javaagent` argument on Surefire. The configured
agent version is therefore the same Byte Buddy version selected by the Boot
dependency management. The targeted Mockito run after the change no longer
emitted the self-attach/dynamic-loading warning; only the unrelated JVM CDS
sharing warning remained.

## Changes implemented

1. Added the static Byte Buddy agent configuration to `pom.xml` without
   disabling security, sandboxing, or assertions.
2. Added `dev-verification` and `full-integration` Maven profiles. The
   profiles use the existing Surefire plugin; no new test runner was added.
3. Kept `SecurityIntegrationTest` in development verification because it is
   `@WebMvcTest`. The full profile includes only the known Docker/Mongo test
   classes rather than relying on the misleading class-name suffix alone.
4. Reworked `CartesiaSpeechSynthesisTest` to stub the HTTP client and assert
   URI, authorization, request JSON, and response bytes without opening a
   loopback server. Production behavior is unchanged when the normal
   constructor is used; the injectable client is package-private and test
   focused.
5. Added the `@Autowired` marker to the normal Cartesia constructor because
   the test-only constructor overload otherwise makes Spring's constructor
   selection ambiguous.

No assertion was weakened, no test was disabled, and no business rule was
changed. Existing uncommitted user changes in `SpeakingSessionServiceTest` and
the two new speaking/config tests were preserved.

## Standard verification workflow

Development verification, suitable for Codex without Docker, is:

```text
mvn -q -Pdev-verification test
```

This runs unit tests, web-slice/component tests, architecture tests, and local
external-adapter tests. It does not run the Docker/Mongo classes listed in the
profile.

Full integration verification, suitable for a local machine or CI runner with
Docker, is:

```text
mvn -q -Pfull-integration verify
```

The full profile selects only the Docker/Mongo integration classes. A successful
Maven exit code with Docker tests skipped is not evidence that Mongo behavior
was verified; the report must show executed tests rather than assumptions.

Status vocabulary:

- PASS: the test executed and assertions passed.
- FAIL: the test executed and an assertion or test error failed.
- BLOCKED: execution could not verify the behavior because a required external
  capability was unavailable, such as Docker or a permitted loopback socket.
- SKIPPED: the test framework deliberately skipped the test and recorded the
  reason, commonly an existing Docker assumption.
- NOT RUN: the command or test did not reach execution, or no trustworthy exit
  result was observed.

## Validation performed

Observed results in this environment:

| Command | Result | Evidence |
|---|---|---|
| `mvn -q -DskipTests compile` | PASS | exit 0 |
| targeted Mockito tests before the fix | PASS with warning | dynamic Mockito/Byte Buddy self-attach warning |
| targeted Mockito tests after the fix | PASS | exit 0; self-attach warning absent |
| `mvn -q -Dtest=CartesiaSpeechSynthesisTest test` after test-first change | EXPECTED FAIL | compile failure because injectable constructor did not yet exist |
| same Cartesia test after implementation | PASS | exit 0; no loopback server |
| `mvn -q -Pdev-verification test` | PASS | exit 0; 170 tests, 0 failures, 0 errors |
| `mvn -q -Pfull-integration test` | PASS as a command, integration behavior BLOCKED | exit 0; 14 Docker/Mongo test cases recorded skipped; 3 LearningPath classes stopped before test execution at Docker assumption |
| `mvn -q verify` | NOT RUN to completion | no trustworthy exit code observed before the constrained runner stopped producing output; Docker probe logged named-pipe access denied |

The Docker probe itself failed with access denied for the Docker named pipe.
Therefore Mongo/Testcontainers behavior remains BLOCKED in this environment.

## Remaining verification plan

On a machine or CI runner with a working Docker daemon:

1. Run `mvn -q -Pdev-verification test` and require 0 failures/errors.
2. Run `mvn -q -Pfull-integration verify` and confirm the Mongo/Testcontainers
   classes execute rather than being skipped or ending at `assumeTrue`.
3. Review the Surefire XML reports for the executed count and retain the
   Docker version, Mongo image, and Java version in CI logs.
4. Run the default `mvn -q verify` as the final project-level check if the CI
   runner permits the repository's normal lifecycle.

The tests that must run with Docker are
`MongoCollectionBootstrapIntegrationTest`, the three topic controller
integration classes, `UserTopicItemAttemptOptimisticLockingIntegrationTest`,
and the three `LearningPath*IntegrationTest` classes. External credentials
are not required by the audited adapter tests; live provider smoke tests, if
added later, should be a separately explicit external-service suite.
