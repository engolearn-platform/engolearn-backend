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

Collections: `assessment_papers`, `assessment_attempts`,
`assessment_gates`, `assessment_grant_revocations`, `assessment_paper_drafts`,
and `assessment_audio_assets`. The attempt embeds a copied
`AssessmentPaperSnapshotVo`, not an indexed paper root. History paginates/project rows
without loading question snapshots. Indexes belong to models and feature configuration.
The per-user gate serializes actions in real Mongo replica-set transactions. Consumption
joins the start transaction; failed profile/attempt writes roll back consumption.
The selected paper and audio are validated outside that transaction. The final start
rechecks eligibility and publication while holding the same series gate as publish
and archive. The timer starts after those checks. A withdrawn version returns
`PAPER_CHANGED` before creating an attempt or consuming quota. Retries
are bounded. Request/command IDs and answer sequences protect replays and stale writes.

Assessment gets an owned, completed baseline through `PlacementBaselineLookup`.
When there is no valid baseline or prior result, an eligible new attempt is blocked.
The test-only baseline adapter can fill a missing Placement baseline in `dev & !prod`.
Demonstration papers and attempts are excluded elsewhere. The User contract stores
the Placement starting level, latest periodic level, highest periodic level and last
valid attempt in `user_profiles.assessment_progress`. The latest periodic level can
decrease. For an official paper, `UserLevelPromotion` advances the shared learning
unlock level `user_profiles.cefr_level` monotonically in the same result transaction.
Existing rows in `assessment_profiles` are read only when embedded progress is absent.
The next valid submission migrates their progress to the existing User profile without
replacing its display name or unlock level. Keep the legacy data during rollout and let
User repair missing profiles before retrying a write.
The Learning Path contract exposes native results without writing plans. Official submission
returns a retryable `ASSESSMENT_LEVEL_SYNC_UNAVAILABLE` if User cannot promote its profile.
The result transaction rolls back. A normal registered user has a User profile; repair
missing profiles in imported accounts through the owning User flow before retrying.

## Learning Path result contract

`AssessmentResults` is the public in-process result provider. `latest(userId)` remains available.
`findCompleted(userId, assessmentAttemptId)` reads that exact owner's READY result, rather
than selecting the latest. `findCompletedAttemptIds(userId, ids)` checks a batch with one
owner/status-filtered query and returns only valid IDs. Missing, foreign, unfinished and
hidden demonstration sources are absent; demonstration visibility remains `dev & !prod`.

DTOs include native attempt/paper IDs, submitted time, level and all measured skill codes,
counts, percentages and contract band values. Returned collections are defensive copies.
The calls never settle or grade an active attempt, consume quota, or write grants/results.
They are independent of full-analysis display grants, as the earlier `latest` contract was.
The frontend result/review APIs still apply their existing Subscription display policy.

Learning Path owns eligibility, pool-name mapping, frozen baselines and path creation.
Assessment does not write Learning Path state or populate the legacy `attempts` /
`micro_skill_results` collections. See the [Learning Path contract](learningpath/learning-path-list-api.md).

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
| POST /attempts/{id}/finish-stage | unitIndex 10..13 and phase READING_PREVIEW, LISTENING_WAIT or ANSWER, UUID commandId; actual snapshot/unit/phase must match |
| POST /attempts/{id}/submit | confirmed=true, binding; grade at most once |
| POST /attempts/{id}/abandon | confirmed=true, owner; also clears an old active conflict |
| POST /attempts/{id}/audio | unitIndex 0..25, UUID issueId, binding; one issuance/unit, identical retry allowed |
| POST /attempts/{id}/audio-failure | unitIndex, reason LOAD_FAILED/PLAYBACK_FAILED/AUTOPLAY_BLOCKED, binding |
| GET /attempts/{id}/result | READY only; two/five skill rows according to retained grants |
| GET /attempts/{id}/review | READY only; original selections/key/explanations/transcripts |
| GET /attempts/{id}/review/audio/{index} | READY owner only; replay allowed |

Version-2 finish-stage accepts only Listening passage indices 10/11 in LISTENING_WAIT or ANSWER. Legacy
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

In `dev`, the development fixture reads PCM WAV from `ASSESSMENT_MEDIA_DIRECTORY`,
defaulting to the bundled sample folder. Uploaded draft files use Media in both profiles.
Studio preview of a saved revision and publication verify every listening WAV. Start
verifies the opening WAV and performs the existing reference checks for later units.
Media reads the delivered WAV signature using a fresh URL. Local dev fixtures retain
their file, digest and duration checks. A failure reports the affected unit, keeps the
draft and live paper intact, or leaves Start without an attempt and quota usage.
The checks run before the write transaction, which rechecks the paper or draft revision
and, for Start, the learner eligibility. Later playback uses its existing failure flow.

In `prod`, playback resolves protected URLs through `MediaManagement` after binding,
phase or review authorization. For a taking request the server resolves delivery outside
the transaction and rechecks the unit, deadline and issue ID before accepting issuance.
The same issue ID may retry issuance while the unit is
open. New papers and snapshots store `audio_media_id` where available. A migrated legacy
snapshot resolves its original SHA-256 through `assessment_audio_assets`, preserving its
content and timestamps. Setup of the separate sound-check reference is explicit.
Missing media reports a service failure. Tests and scripts are described in the
[production handoff](../../tools/assessment/README.md).

The Assessment limits are 4,000,000 bytes per WAV, 28 seconds for a new single, 50 seconds
for a legacy single or sound check, and 120 seconds for a group. Check the original
audio and transcript before release. Producer preflight validates content before quota
consumption. Taking keeps its existing lack of replay, seek and rate controls. Review
allows seeking and replay, with fresh authorization on a delivery retry. Audio failures
do not extend deadlines, issue attempts or refund quota. Signed URLs remain usable on
the provider until their short expiry, regardless of the browser controls.

Premium assessment count is provider-configured. The current subscription-development-sample uses Free 1, Trial 2,
Premium 5 and consistent displayed benefits; this is not a global hardcoded entitlement.
Already issued Subscription snapshots are preserved. Full-analysis grants survive ordinary
expiry. A verified refund must call `AssessmentGrantRevocation` with the exact correlated
source (`scope:snapshotId:effectiveAt`), preserving other grants and scores. The
Subscription outbox publishes only after accepting the correlated full refund. Assessment
consumes the fact idempotently; ordinary entitlement expiry emits no revocation.
Historical refunds already processed before this change need a separately reviewed backfill.

Before public release, review questions and WAVs, configure HTTPS and private secrets,
validate Mongo transaction permissions and backup/restore, register historical audio, and
run full CI plus browser acceptance on staging. Per-second state polling currently writes
serialized state; measure concurrency before making capacity/availability claims.

## Running and verification

The local handoff package includes the Windows and Atlas instructions, isolated demo data and reset guide.

### Shared development data

Pull this backend branch to get the [shared Assessment demo dataset](../../tools/mongo-json-importer/samples/assessment-demo.json)
and its [WAV files](../../tools/mongo-json-importer/samples/assessment-media). The paper is marked
`demonstration: true` and is selectable in `dev` without `prod`.

1. Follow the [importer setup](../../tools/mongo-json-importer/README.md) for Docker Compose
   and both local `.env` files. Use a development database with Mongo replica-set transactions.
   Confirm the importer target database matches the backend's `APP_DB` and keep the conflict
   policy at `skip`.

   ```powershell
   .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-demo.json -DryRun
   .\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\assessment-demo.json
   ```

   Run the second command only after confirming the target and the preflight report. It
   inserts the paper if its ID is absent. If preflight reports the paper already exists,
   `skip` keeps the existing copy. Use a fresh dev database to test this exact fixture if the
   existing copy is different.

2. If the shared development Subscription catalog is absent, import it separately using
   the explicit [Subscription sample](../../tools/mongo-json-importer/README.md#development-subscription-catalog)
   command against the same local database. The Assessment sample already contains the demo
   account's initial Subscription record. Then run the backend in profile `dev` with the media
   path below; there is no startup publication flag.

   ```powershell
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--engolearn.assessment.media-directory=./tools/mongo-json-importer/samples/assessment-media'
   ```

3. Run the frontend from `feat/proficiency-check` and open `/assessment` as an authenticated
   test learner. The learner needs a Subscription account with available
   `PROFICIENCY_CHECK_ATTEMPT` quota and a valid baseline or existing valid assessment result.
   Verify `GET /api/v1/quota/me/usage` and `GET /api/v1/assessment/preparation` return 200,
   and preparation reports `paperAvailable: true` and `baselineValid: true`.
   Then use the sound check, start and submit a test attempt, and verify the new entry in History.

For the signed-in Placement-to-Assessment journey on a clean development machine,
use [the isolated local demo](../../tools/assessment/README.md#chạy-placement--đánh-giá-định-kỳ-trên-máy-thử).
It seeds a published Placement form and a separate published version-2 periodic paper.
`baselineValid` means the account already qualifies by result. `paperAvailable`
means the current environment has a published periodic paper eligible for selection.
A valid Placement result alone cannot make a periodic paper available.

The paper-only import does not create a login or supply Placement results. For a newly registered
test account in an isolated development database, register it normally after importing the
development catalog; normal registration creates its Subscription account. Do not use the
retired account-only manifest. For a test-only baseline, import a separate local JSON file with
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


The version-2 paper now ships inside `assessment-demo.json`, together with the public demo
account and four historical attempts. For the shared Learning Path dataset, Subscription
account initialization and current demo limits, follow the
[importer guide](../../tools/mongo-json-importer/README.md#shared-assessment--learning-path-demo).
Learning Path Listening audio placeholders do not affect the 13 supplied Assessment WAV files.

## F03 — quản trị và xuất bản đề

`CONTENT_MANAGER` và `ADMIN` có quyền dùng `/api/v1/assessment/admin/drafts`. GET trả danh sách tóm tắt theo trang (tham số `page` từ 0, 25 mục). POST nhận `series` và `name`. Nếu series đã có đề, sao chép phiên bản lớn nhất sang nháp kế tiếp. Nếu chưa có, tạo bản 1 rỗng. GET và PUT tại `/{series}` mở và lưu nội dung. PUT nhận tên, kỹ năng, phần/câu và revision lấy từ lần đọc gần nhất. Cho phép lưu thiếu nội dung và đáp án. Giao diện dùng null cho đáp án chưa chọn và 0–3 cho A–D.

Nháp ở collection `assessment_paper_drafts`, khóa duy nhất theo series. Tạo trùng trả 409 DRAFT_EXISTS. Ghi theo khóa và revision trong một cập nhật MongoDB. Ghi cũ trả 409 STALE_DRAFT. Máy chủ giữ audio được sao từ đề nguồn theo vị trí hoặc gắn WAV mới bằng SHA-256
đã đăng ký. Các thao tác quản trị đều qua kiểm quyền ở service.

- POST `/{series}/preview` với revision để kiểm ma trận và xem nội dung. GET
  `/{series}/preview/audio/{unit}?revision=...` cấp audio xem thử. Preview thành công ghi người xem đúng revision. Không tạo lượt hay trừ quota.
- GET `/skill-catalog` cấp mã, tên và phần thi từ contract Assessment. Phiên bản 2 chỉ
  nhận mapping đã thống nhất với Learning Path; phạm vi hiện có là R01, R02, R04, L01, L03.
- POST `/{series}/review` xác nhận chính người đó đã mở preview cho revision hiện tại.
  PUT lưu mới xóa dấu đã xem và xác nhận cũ.
- POST `/{series}/publish` yêu cầu revision đã duyệt. Việc tạo bản xuất bản,
  ngừng cấp phiên bản trước và bỏ nháp nằm trong một giao dịch MongoDB. Media được
  chuẩn bị trước giao dịch và revision được kiểm lại trong giao dịch.
  Gửi lại revision đã xuất bản trả về đúng phiên bản đó.
- DELETE `/{series}?revision=...` chỉ xóa nháp khớp revision. GET
  `/api/v1/assessment/admin/papers` liệt kê phiên bản. POST
  `/api/v1/assessment/admin/papers/{series}/{version}/archive` ngừng cấp đúng phiên bản đó.
  Đề đã phát hành, lịch sử và WAV còn dùng được giữ nguyên. Media phase 1 chưa có xóa file
  công khai, nên việc dọn asset không còn tham chiếu phải qua quy trình vận hành riêng.

## Version 2 listening navigation

During the two-second preparation, Next on an unanswered single sends the current question
with a null choice. The server locks that single and opens the next unit. Choices become
editable when preparation ends. Inside a five-question group Next moves focus locally,
retaining the same audio, editable scope and deadline. Confirming the end of the current
group may close it during preparation. A confirmation crossing the audio-start boundary
remains valid for the same unit. Old-unit and duplicate commands cannot close a later group.

Admin draft creation may supply expectedSourceId. If supplied, the latest paper must match that ID under the series transaction or the server returns 409 DRAFT_SOURCE_CHANGED. The local import instructions are in tools/assessment/README.md.

### Studio micro-skill and workbook import

The Studio options endpoint reads `micro_skills`. Its stable document ID is the PFC
code. `label` is the current shared display name, `section` is READING or LISTENING,
`legacy_name` is the established Learning Path pool key, and `active` controls selection
in Studio. The confirmed compatibility mapping covers R01/R02/R04/L01/L03. Install
the missing five records using the scoped catalog import on the test database.
Use the rollout instructions in the [Mongo importer guide](../../tools/mongo-json-importer/README.md).

ADMIN can list, create, rename and retire via `/api/v1/assessment/admin/micro-skills`.
New codes without a confirmed mapping are stored pending. Rename and retirement
leave existing paper/attempt snapshots and legacy pool keys intact. New preview and
publication reject a retired mapped code. Learning Path recommendations retain their
legacy `name` field as the creation key and read the new optional `displayName`
from the shared contract. Broader Learning Path catalog and question-bank migration
remain with the Learning Path owner. Reconcile the collection contract before
adding production mappings beyond the five confirmed entries.

ADMIN and CONTENT_MANAGER use `/api/v1/assessment/admin/draft-imports`.
Download the template envelope at GET `/template`. POST `/inspect-audio` validates a
single WAV and returns its digest and duration. POST `/preview` accepts multipart
`workbook` and JSON part `manifest`. It validates the three template sheets and
reuses the Assessment paper matrix. Preview uses the selected local WAVs in the
browser. The inputs use 50 questions, 26 units and 12 distinct PCM WAVs.
`De.AmThuWav` can reference a separate, optional WAV (at most 50 seconds).

After confirmation, the browser sends each checked WAV and its digest through
the existing protected Assessment-to-Media endpoint. The final multipart POST
accepts the same workbook, manifest and preview `token`, verifies registered Media
against the manifest, then writes the draft and one durable import receipt in
the existing series transaction. Retries reuse registered assets and return the
same draft. A completed receipt prevents the same package from creating a new
draft after publication or deletion. An upload interrupted before commit can leave
reusable Media assets. Existing draft preview, review and publication remain
bound to the saved revision. Use the separate sound-check action to change the
global WAV. Dev can use its packaged sound before a global asset is registered.
