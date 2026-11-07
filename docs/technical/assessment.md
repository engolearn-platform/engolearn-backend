# Periodic assessment — learner F01/F02

Current implementation: approved learner rules dated 2026-11-05, `rules_version=2`.
Scope includes preparation, transactional start, taking, submission, grading, history,
result and snapshot-based review. F03 administration and Learning Path authoring are excluded.

## Timing and navigation

| Stage | Questions | Server window | Navigation |
| --- | --- | --- | --- |
| Listening singles | 1–10 | 30s including 2s preparation and audio | Only current question; Next saves and locks it |
| Listening group 1 | 11–15 | One 300s window including 2s preparation and audio | All five answers editable in any order |
| Listening group 2 | 16–20 | One 300s window including 2s preparation and audio | All five answers editable in any order |
| Reading | 21–50 | One 1800s window | Any Reading question editable until submission/expiry |

Groups use a single five-minute timer, with no 120s/180s split. Confirmed finish-stage
may end an open group early and locks it. No time carries forward. Listening cannot be
revisited from a later unit/Reading. Reading has no preview timers. Selecting question 50
does not submit; the explicit confirmed Submit action ends the attempt. The existing
ability to submit early remains available in all stages. Maximum elapsed duration when
all deadlines are used is 2700 seconds (45m), including twelve preparation intervals.

`AssessmentTimeline` is a pure feature-owned domain transition helper in `model`.
Deadlines use Mongo millisecond precision and are authoritative on the server. Actions,
reads and the expiry scheduler catch up from elapsed deadlines, without granting a new
window to a delayed client. Terminal time comes from the actual final deadline. A late
abandon cannot overwrite a valid result. A saved wrong answer can produce a valid zero/A1;
no saved answers produces `NO_RESULT`, which has no score or review. Cooldown is 21 days
from a valid submitted result. Creating an attempt successfully consumes one quota unit;
abandonment does not return it. These rules are unchanged by version 2.

## Compatibility and publishing

New starts select only published version-2 papers. If only legacy papers exist, preparation
reports no eligible paper and start returns `NO_PAPER` before consuming quota. Publish a
new paper ID/version after validating its content/media; do not overwrite old published
paper/media documents or re-number stored attempts.

Snapshots without `rules_version` (or an earlier version) retain legacy Reading-first
numbering and timing: 60s singles; Reading previews 90/120/150/240s plus 300s; Listening
groups 120s plus 180s. Existing legacy active snapshots keep the 01/11 early-preview,
immediate completed-group and final-Next behavior. History/review use their own snapshots.
Version 2 intentionally supersedes the old Reading preview and final-question navigation.
Scoring derives sections from the snapshot units, never a question-number threshold.

## Structure, transactions and contracts

Feature root: `fit.iuh.engolearn.assessment`. Business interfaces are in `service`,
implementations in `service.impl`, Mongo access in `repository`, scheduling adapters in
`infra.scheduling`, HTTP advice in `controller.exceptionhandler` and wiring in `config`.
Controllers return typed `ApiResponse` DTOs; failures use `ProblemDetail`. No Maven
module/dependency has been added. Root documents extend `BaseDocument`; embedded values
implement `EmbeddedDocument`; owned persisted fields have explicit snake_case mappings.

Collections: `assessment_papers`, `assessment_attempts`, `assessment_profiles`,
`assessment_gates`, `assessment_grant_revocations`. The attempt embeds a copied
`AssessmentPaperSnapshotVo`, not an indexed paper root. History paginates/project rows
without loading question snapshots. Indexes belong to models and feature configuration.
The per-user gate serializes actions in real Mongo replica-set transactions. Consumption
joins the start transaction; failed profile/attempt writes roll back consumption. Retries
are bounded. Request/command IDs and answer sequences protect replays and stale writes.

Assessment calls Subscription/Quota only through their public contracts and does not read
their collections. It does not write User or Learning Path state. Existing result-provider
contracts remain unchanged. A real Placement integration must supply
`AssessmentBaselineSource`; absent baseline blocks starting. The dev baseline adapter is
active only in `dev & !prod`. Demonstration papers/attempts are excluded outside development.

## HTTP API

Base `/api/v1/assessment`. All routes require the shared authenticated principal from
current main's real cookie authentication; `X-Dev-User-Id` has no authentication effect.
All responses are `Cache-Control: no-store`; owner lookup returns 404 for another owner.
Taking also requires the in-memory `X-Assessment-Binding` capability. Bodies reject
missing/unknown fields, fractional integers, invalid UUIDs and payloads above 4 KiB.

| Method and suffix | Contract |
| --- | --- |
| GET /preparation | Baseline, levels, independent cooldown/quota, active conflict, eligible-paper availability |
| GET /sound-check | Authenticated WAV sample; no quota use |
| POST /attempts | UUID requestId/binding, confirmed=true, soundChecked=true; same request replays |
| GET /attempts?page=0&size=20 | Owner terminal history; maximum page size 100 |
| GET /attempts/{id} | Current state; requires binding |
| PUT /attempts/{id}/answer | question 1..50, sequence 0..JS-safe integer, choice null/0..3, UUID commandId |
| POST /attempts/{id}/next | Same body; version 2 only permits the open Listening single |
| POST /attempts/{id}/finish-stage | unitIndex 10..13 and phase READING_PREVIEW or ANSWER, UUID commandId; actual snapshot/unit/phase must match |
| POST /attempts/{id}/submit | confirmed=true, binding; grade at most once |
| POST /attempts/{id}/abandon | confirmed=true, owner; also clears an old active conflict |
| POST /attempts/{id}/audio | unitIndex 0..25, UUID issueId, binding; one issuance/unit, identical retry allowed |
| POST /attempts/{id}/audio-failure | unitIndex, reason LOAD_FAILED/PLAYBACK_FAILED/AUTOPLAY_BLOCKED, binding |
| GET /attempts/{id}/result | READY only; two/five skill rows according to retained grants |
| GET /attempts/{id}/review | READY only; original selections/key/explanations/transcripts |
| GET /attempts/{id}/review/audio/{index} | READY owner only; replay allowed |

Version-2 finish-stage accepts only Listening passage indices 10/11 in ANSWER. Legacy
preview indices 10..13 remain valid only for legacy snapshots. Replaying an earlier command
cannot extend a deadline or end the next group. Version-2 taking state adds `rulesVersion`,
`audioStartsAt`, and `questions` (one single, five group questions or all thirty Reading
questions with per-unit passage). `LISTENING_WAIT` locks answers for the two-second delay.
No correct answer, explanation or transcript is sent in taking state. State revisions let
FE ignore older responses. Existing scalar fields remain for legacy taking.

During Listening preparation, the public deadline already points to the end of the
30-second or 5-minute window. The internal transition opens answers at audioStartsAt,
two seconds after the unit begins, without adding time to the deadline.

The binding, answers and official audio are never persisted by FE. Reload/new document
cannot resume. Pagehide/SPA departure attempts authenticated keepalive abandonment;
visibility changes do not abandon. When abrupt close prevents delivery, server time keeps
running until expiry. There is no pause, resume or clock-bypass endpoint.

## Media, analysis access and release boundaries

`engolearn.assessment.media-directory` defaults to `./assessment-media`. Immutable PCM WAV
files are SHA-256 checked and at most 4 MB. Version-2 single audio is at most 28s; legacy
single audio at most 50s; passage audio at most 120s. Twelve distinct official paths plus
`sound-check.wav` are required. Validate all resources before consumption. Normal official
playback has no replay/seek/rate controls; review may replay. Audio-failure observations do
not extend deadlines, issue attempts or refund quota. Browser playback cannot provide DRM.

Premium assessment count is provider-configured. The isolated demo uses Free 1, Trial 2,
Premium 2 and consistent displayed benefits; this is not a global hardcoded entitlement.
Already issued Subscription snapshots are preserved. Full-analysis grants survive ordinary
expiry. A verified refund must call `AssessmentGrantRevocation` with the exact correlated
source (`scope:snapshotId:effectiveAt`), preserving other grants and scores. Current main
includes Payment/Subscription durable messaging, but an Assessment refund consumer has not
been added in this change. Entitlement expiry must not be treated as a refund.

Before public release: integrate real Placement/baseline and exact refund delivery,
professionally review questions/audio, configure HTTPS/secure cookies and private secrets,
validate Mongo transaction permissions and backup/restore, retain historical assets, and run
full CI plus real-browser acceptance on staging. Per-second state polling currently writes
serialized state; measure concurrency before making capacity/availability claims.

## Running and verification

The local handoff package includes the Windows and Atlas instructions, isolated demo data and reset guide.

### Shared development data

Pull this backend branch to get the [version-2 sample paper](../../tools/mongo-json-importer/samples/assessment-paper-v2.json)
and its [WAV files](../../tools/mongo-json-importer/samples/assessment-media). The paper is marked
`demonstration: true` and is selectable in `dev` without `prod`.

1. Follow the [importer setup](../../tools/mongo-json-importer/README.md) for Docker Compose
   and both local `.env` files. Use a development database with Mongo replica-set transactions.
   Confirm the importer target database matches the backend's `APP_DB` and keep the conflict
   policy at `skip`.

   ```powershell
   .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-paper-v2.json -DryRun
   .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-paper-v2.json
   ```

   Run the second command only after confirming the target and the preflight report. It
   inserts the paper if its ID is absent. If preflight reports the paper already exists,
   `skip` keeps the existing copy. Use a fresh dev database to test this exact fixture if the
   existing copy is different.

2. Run the backend in profile `dev` with the media path below. If the development catalog
   has not been published, follow the [Subscription development seed](../../tools/subscription/README.md)
   for that same database. To combine both startup options for the initial catalog publication,
   append ` --engolearn.subscription.publish-manifest=tools/subscription/dev-main-manifest.json`
   inside the quoted `-Dspring-boot.run.arguments` value.

   ```powershell
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--engolearn.assessment.media-directory=./tools/mongo-json-importer/samples/assessment-media'
   ```

3. Run the frontend from `feat/proficiency-check` and open `/assessment` as an authenticated
   test learner. The learner needs a Subscription account with available
   `PROFICIENCY_CHECK_ATTEMPT` quota and a valid baseline or existing valid assessment result.
   Verify `GET /api/v1/quota/me/usage` and `GET /api/v1/assessment/preparation` return 200,
   and preparation reports `paperAvailable: true` and `baselineValid: true`.
   Then use the sound check, start and submit a test attempt, and verify the new entry in History.

The paper import does not create a login, publish Subscription policy or supply Placement results.
For a newly registered test account in an isolated dev database, first publish the dev catalog,
then publish a separate account-only manifest through the same Subscription startup option:

```json
{
  "manifestId": "assessment-dev-reviewer-account-unique-id",
  "environment": "development-only",
  "plans": [],
  "policies": [],
  "accounts": [
    { "userId": "<id from GET /api/v1/auth/me>", "trialUsed": false, "provenance": "NEW_ACCOUNT" }
  ]
}
```

Use a unique manifest ID and only provision an account verified as newly registered and missing
its Subscription record. Keep this local manifest under `.artifacts/`. For a test-only baseline,
import a separate local JSON file with
`{"collections":{"assessment_dev_baselines":[{"_id":"<same user id>","valid":true,"level":"A2"}]}}`
using the same dry-run workflow. Set `MONGO_IMPORT_OBJECT_ID_FIELDS=none` in the importer-local
`.env` for that file so the baseline lookup sees a **string** key. Restore the previous importer
setting before importing datasets with other ID conventions. Keep this test baseline outside
the shared sample set. It is read only in `dev` without `prod`.

If Docker is unavailable, use the separate Atlas demo launcher from the handoff package.

Final command: `./mvnw verify` with JDK 21+. Feature checks: `AssessmentRulesTest`,
`AssessmentMediaTest`, `AssessmentVersionTwoTest`, `AssessmentMongoIntegrationTest`,
`ArchitectureTest`. Set `ASSESSMENT_TEST_MONGO_URI` only to a real replica set: the Mongo
tests use/clear test-owned collections in the dedicated `engolearn_pfc_test` database.
Legacy tests import an already-started legacy snapshot; actual new starts require version 2.
No fake assessment HTTP or in-memory Mongo replacement is used as integration evidence.
