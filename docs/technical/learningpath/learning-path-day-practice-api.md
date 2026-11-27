# Start a Learning Path practice day

`POST /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/start`
returns the shared `{ data, timestamp }` envelope. The user ID comes from the
authenticated access cookie. `pathId` is a MongoDB ObjectId; `weekNo` is 1–3
and `dayNo` is 1–5. The path must belong to the authenticated user,
the week must be open, and the day must be available before its deadlines.

Owner IDs are BSON strings in paths and question snapshots, including non-ObjectId account
IDs. Start, draft, submission, and review use the exact authenticated string for ownership
checks. See [user ID storage](learning-path-list-api.md#user-id-storage).

On the first successful request, the day becomes `IN_PROGRESS`. The server stores
`started_at`, the `yyyy-MM-dd` date in `Asia/Ho_Chi_Minh`, and `auto_close_at` at
the next local midnight. The path becomes `IN_PROGRESS` if it was `NOT_STARTED`.
The update sets `next_run_at` to the earlier of its current value and the new day deadline.
These fields are updated conditionally so concurrent first requests cannot restart
the day. A repeat request while the day is still open returns the original times
and the same ten questions in their saved order.

The response `data` contains `pathId`, `weekNo`, `dayNo`, `status`, `startedAt`,
`autoCloseAt`, and `questions`. Each question contains `order`, `questionId`,
`microSkill`, `skill`, `prompt`, four `{ optionId, text }` options, and an optional
`passage` with `title`, `text`, and optional
`audio { url, expiresAt, contentType, durationSec, format }`.
It also contains `selectedOptionId`, which is null until a draft is saved and
restores the latest selection when an in-progress day is reopened.
Content is read from the immutable `learning_path_questions` snapshots, never
from the mutable question pool. The response type has no correct option,
explanation, or transcript field.

Errors use `ProblemDetail`: a missing or invalid session returns 401; malformed parameters return 400; a path not owned by
the user or a nonexistent week/day returns 404; a locked, closed, completed,
cancelled, or expired day returns 409. Missing or inconsistent saved question
snapshots are server errors and do not start a new day.

## Save or change one answer draft

`PUT /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/questions/{questionId}/draft`
accepts `{ "selectedOptionId": "A" }`, where the option is one of A–D and must
exist in that question's saved snapshot. Each successful call replaces the
selection for that question and returns `questionId`, `selectedOptionId`, and
`savedAt` in the shared response envelope. A repeat call can change the answer.
The server saves the draft inside the Learning Path, updates the local activity
date and inactivity cancellation deadline, and recalculates `next_run_at`.
It does not grade the draft or return the correct option.

The path must belong to the user; the week and day must still be open and the
day must already be in progress. A question that has been finalized or a day
whose deadline has passed cannot be edited. Unknown path/week/day/question
returns 404, invalid option returns 400, and a closed or expired state returns
409. The atomic update also checks the saved deadline and item state to guard
against concurrent closure.

## Submit a practice day

`POST /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/submit`
requires no request body. The path must belong to the user, the week must be open,
and the day must already be in progress and before its deadline. The server
finalizes and grades the saved drafts atomically. Blank questions remain unanswered;
a fully answered day becomes `COMPLETED`, otherwise it becomes `SUBMITTED` with
close reason `SUBMITTED`. The response envelope contains `pathId`, `weekNo`,
`dayNo`, `status`, `closedAt`, `assigned`, `answered`, `correct`, `wrong`,
`completionRate`, and `practiceScore` (null when no answers were saved).

The endpoint returns 404 for an unknown path, week, or day and 409 when the
day cannot be submitted, including a repeat submission or an elapsed deadline.
Weeks 1 and 2 wait until midnight before closing. In week 3, a submission closes
the week and path immediately if every practice day has been submitted or
already closed, regardless of how many questions were answered.
Submission, draft saves and the lifecycle job use a path revision to detect
concurrent writes; older documents without a revision are supported.

## Review a practice day

`GET /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/result`
returns the shared response envelope for a day in `COMPLETED`, `SUBMITTED` or `CLOSED` state,
including days closed automatically at midnight or when their week ended. The path
must belong to the user and must not be cancelled. A missing path, week or day
returns 404; a cancelled path or a day that has not closed returns 409.

The response contains `pathId`, `weekNo`, `dayNo`, `status`, `closedAt`,
`assigned`, `answered`, `correct`, `wrong`, `completionRate`, `practiceScore`,
and `questions` in the original order. `practiceScore` is null when no answer
was saved. Each question contains `order`, `questionId`, `microSkill`, `skill`,
`prompt`, `options`, optional `passage` including audio and transcript,
`selectedOptionId`, `correctOptionId`, `explanation`, and `answerStatus`
(`CORRECT`, `WRONG`, or `UNANSWERED`). Unanswered questions have a null
selection and still include the correct option and explanation. Question content
comes from the saved snapshots; missing or inconsistent result data is a server error.

Legacy days stored as `CLOSED` with `close_reason: SUBMITTED` are returned as
`SUBMITTED` by both the detail and result APIs. Other closed days remain `CLOSED`.

## Snapshot audio and delivery renewal

New snapshots copy the stable `passage.audio_media_id` when present and do not
copy its expiring delivery URL or legacy recording metadata. Creation resolves
the selected passages through `media.contract.MediaManagement` before writing
snapshots/path; response URLs are discarded. Existing standalone creation and
orphan-snapshot limitations are unchanged. No Media collection or migration is added.

Start and result resolve Media from the owned snapshot after checking access,
never from the current pool passage. Replacing/retiring a source or changing its
audio reference does not switch the recording assigned to an existing path.
Managed delivery returns URL, `expiresAt`, content type and a format derived from
the audio MIME type; duration is null because the Media delivery contract does
not provide it. Legacy snapshots without a Media reference continue returning
their stored audio URL/format/duration, with null expiry/content type. A present
but invalid Media reference fails; missing/unavailable Media never falls back to
the legacy recording. Provider details are not exposed in errors.

`GET /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/questions/{questionId}/audio`
returns `{ data: { url, expiresAt, contentType, durationSec, format }, timestamp }`.
It is authenticated, uses the trusted session user and returns `Cache-Control: no-store`.
Start/result responses also explicitly use `no-store` for delivery URLs.

- A foreign/missing path, week/day, question outside that day, or question without
  audio returns 404. IDs/ranges use the same validation as day APIs (400).
- A day must already be IN_PROGRESS and still satisfy the start flow's path,
  week/day deadlines and inactivity guards, or be COMPLETED/SUBMITTED/CLOSED and
  accessible under the result flow's non-cancelled rule. NOT_STARTED/expired
  in-progress/cancelled access returns 409. Completed-day audio remains readable
  after its practice deadline, just like review.
- Snapshot lookup is scoped by path ID, owner and the question assigned to that day.
  Duplicates/missing/inconsistent saved data are server errors, not pool fallback.
- This GET does not start/resume a day, extend deadlines/activity, save drafts,
  grade, select new questions, or modify any snapshot/path/source/Media asset.
  It returns only audio delivery data, without transcript, options or answer fields.
- Missing Media returns 404 `MEDIA_NOT_FOUND`; unavailable delivery or a non-audio
  asset returns 503 `MEDIA_STORAGE_UNAVAILABLE`. No generic public Media endpoint,
  manual Cloudinary access or automatic provider retry is introduced.

Frontend expiry recovery and real Cloudinary playback/seeking have now been
verified in steps 4–5. Publication readiness no longer adds the temporary
`LEARNER_AUDIO_DELIVERY_UNSUPPORTED` blocker. Automated delivery validation and
professional review remain required. See the [live verification record](learning-path-media-verification.md).
Data rollout is recorded separately: [30 ACTIVE passages](learning-path-media-migration-2026-11-27.md)
and [300 legacy Listening snapshots](learning-path-legacy-audio-migration-2026-11-27.md)
now use Media references. Normal ownership/state restrictions still apply.

### Verification (2026-11-27)

`./mvnw.cmd "-Dtest=ArchitectureTest,LearningPath*,QuestionPool*IntegrationTest" "-Dsurefire.useManifestOnlyJar=false" verify`
completed with BUILD SUCCESS: 434 tests passed, no failures/errors/skips. This
includes nine architecture checks, 23 new `LearningPathMediaHttpIntegrationTest`
cases and the existing learner/authoring/publication/lifecycle regressions.
Log: `target/learning-path-media-verification.log`. The full repository suite was
not run; verification is scoped to Learning Path and architecture.

New tests use real HTTP cookie authentication, the Spring method-security proxy,
MediaManagement and isolated MongoDB. Only the external Media storage-provider
port is substituted to avoid Cloudinary upload/network operations. They verify
snapshot identity and unchanged content after source edits/retirement, creation
failures before writes, fresh delivery and no-store, secrecy, every inherited
owner role, unauthenticated/foreign/locked callers, practice deadlines and closed
review access, missing/wrong-kind/unavailable Media without legacy fallback,
legacy URLs and malformed/missing/duplicate snapshots. The provider double does
not establish real Cloudinary playback, seeking, URL expiry/recovery or frontend
integration; these remain manual/follow-up checks. No live business DB, source
JSON or local media files were migrated or uploaded.
