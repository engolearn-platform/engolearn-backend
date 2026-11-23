# CI verification

The verify job in [.github/workflows/ci.yml](../../.github/workflows/ci.yml) defaults to
`SPRING_PROFILES_ACTIVE=prod` and runs the full `./mvnw -B -ntp clean verify` command.
The existing change filter, Java 21 setup, Docker check, Sonar analysis and required CI gate remain.
There is no third Spring runtime profile and no developer `.env` in the CI checkout.

## Test-only configuration

| Input | Source and purpose |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod; default Spring test/application configuration |
| MONGODB_URI | mongodb://127.0.0.1:27017/engolearn_ci; syntactically valid test-only URI required by prod configuration validation, not a provisioned database |
| APP_DB | engolearn_ci; test-only database binding required by prod configuration validation |
| MONGO_HOST / MONGO_PORT | 127.0.0.1 / 27017; retained for tests that explicitly select dev and use discrete local Mongo properties |
| APP_USER / APP_PASSWORD | ci_test / ci-only-placeholder; retained for explicit dev-profile test binding, not used by prod when URI is set |
| CORS_ALLOWED_ORIGINS | http://localhost:3000; loopback frontend origin for tests |
| AUTH_JWT_SECRET | Existing GitHub Actions variable CI_AUTH_JWT_SECRET; test-only signing key, at least 32 bytes |
| GEMINI_API_KEY | Deliberately invalid dummy value; permits bean construction in legacy tests explicitly selecting dev |

The workflow validates the selected profile, rejects a root `.env`, and retains signing-key
validation. Do not supply production database credentials or provider keys to make tests pass.
Other optional integration configuration retains the common defaults. Provider tests must use
their own stubs/mocks; the dummy Gemini key is not functional provider integration evidence.

## How integration database connections work

Tests using AbstractMongoIntegrationTest start real disposable Mongo containers with
replica-set support. Their DynamicPropertySource injects the actual container URI and
database with higher priority than YAML/environment defaults. The container URI owns its
connection/authentication settings; the workflow's placeholder credentials do not create a
user in those containers and are not required to match unauthenticated container fixtures.

The workflow does not provision a fallback Mongo server at localhost:27017 and never connects
to Atlas. `MONGODB_URI` is only a test binding value; a test that needs persistence must
provision/inject its own isolated database. Suites based on AbstractMongoIntegrationTest replace
the placeholder URI with their disposable replica-set container through DynamicPropertySource.
Tests with explicit connection properties keep those properties; setting prod is not a
replacement for missing test infrastructure. CI never needs a developer's local database.

## Profile and coverage limitations

This change establishes the CI default, not universal production compatibility:

- Tests with explicit ActiveProfiles or custom property sources may use different behavior.
  Existing dev selections are not rewritten by this workflow change.
- Unit/profile-gate tests may intentionally construct isolated dev/prod contexts.
- AssessmentMongoIntegrationTest still requires ASSESSMENT_TEST_MONGO_URI and skips if it is
  absent. This workflow does not provision that suite's separately selected database.
- Suites conditional on Docker and other prerequisites may skip. Inspect their reports;
  a successful job alone does not prove every integration scenario ran.
- Default SpringBootTest contexts do not generally invoke the main method. This workflow
  runs Maven tests; it does not add a packaged-JAR production startup smoke test.
- Existing incompatible suites, fake-header expectations and missing fixtures are not
  excluded or weakened. Their failures continue to fail verify and the required CI gate.

Module owners must adapt their fixtures, authentication, media storage and explicit profiles
to the [runtime contract](../architecture/runtime-configuration.md) in follow-up changes.

## Verification reports

After Maven, an always-running upload step preserves Surefire, Failsafe (if present) and
JaCoCo (if generated) reports for seven days under
`backend-verification-<run_id>-<run_attempt>`. It uploads only report paths, not the checkout
or environment files. See the official [upload-artifact usage](https://github.com/actions/upload-artifact#usage).

Open the workflow run's artifacts to inspect failing/skipped tests and diagnostics. If
verification failed before reports were generated, the uploader warns; the original failure
is retained. Report upload does not turn a failing test run into success. Sonar retains its
existing success/secret conditions and the CI gate retains its existing failure semantics.

For a local approximation, use a fresh checkout/export without `.env`, supply the test-only
environment values above and an ephemeral local signing key, then run Maven with working Docker.
This is not a substitute for observing a GitHub Actions run on its Ubuntu runner.
