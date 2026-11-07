# Learning Path list API

Base path: `/api/learning-paths`

All endpoints require an authenticated access cookie. The backend uses the session's user ID
for list, search, recommendation, creation, detail, and practice operations. Clients do not
send a `userId`. Missing or invalid sessions return 401; a path or periodic test owned by
another user is not returned.

## User ID storage

Learning Path treats the authenticated user ID as an opaque, case-sensitive string, including
non-ObjectId values such as `pfc-demo-learner-v2`. Store `user_id` as a BSON string in
`learning_paths`, `learning_path_questions`, and the legacy `attempts` / `micro_skill_results`
projections. An existing 24-hex owner ID remains the same text. Account IDs in `users` and
native Assessment collections are not changed. Path `_id`, snapshot `path_id`, source
`attempt_id`, and baseline result IDs retain their existing BSON ObjectId representation.

Fresh datasets must use explicit Extended JSON for ObjectIds with automatic string conversion
disabled; see the [importer instructions](../../../tools/mongo-json-importer/README.md#datasets-with-string-user-ids).

## List

`GET /api/learning-paths?status=<status>` returns list items for the requested
status. `status` defaults to `ACTIVE`; the supported values are `ACTIVE`, `COMPLETED`, and
`CANCELLED`. The response also includes total counts for all three sections.

## Search

`GET /api/learning-paths/search?keyword=<keyword>` searches only the current
user's active (`NOT_STARTED`, `IN_PROGRESS`) and completed paths. It matches either an exact
creation date in `DD/MM/YYYY` format or a case-insensitive partial micro-skill name. Cancelled
paths are excluded, and the search response retains the same list envelope and unfiltered section
counts.

Both endpoints return the shared `ApiResponse` envelope. Search results are ordered by creation
time descending and use the same list item DTO as the list endpoint.

List/search items expose `sourceAssessmentAttemptId: string|null` for navigation to
`/assessment/attempts/:attemptId/result`. This field replaces the former `sourceAttemptId`
response property; backend and frontend must deploy this contract change together.
The backend resolves all source IDs in one batch from `attempts`, filtered by the cookie
owner, `type=PERIODIC` and `status=COMPLETED`. Missing sources, foreign sources and absent,
blank or non-string `assessment_source_attempt_id` values return `null`.
The stored `learning_paths.source.attempt_id` remains an ObjectId and is not changed.
This lookup does not read or modify `assessment_attempts`. Assessment itself checks result
availability, ownership and analysis access when the result page opens.

## Recommendation

`GET /api/learning-paths/recommendation?attemptId=<ObjectId>` reads a
completed periodic test (`attempts.type=PERIODIC`, `status=COMPLETED`), its valid
`micro_skill_results`, active paths, and the question pool. It does not write data. The result
contains `state`, `level`, `durationWeeks=3`, `daysPerWeek`, `questionsPerDay=10`, and
`microSkills` with the test scores. Possible states are `AVAILABLE`, `NO_WEAK_SKILLS`,
`NO_ELIGIBLE_SKILLS`, and `ALREADY_CREATED`; the last includes `existingPathId`.

Only results below 50% qualify. A micro-skill already in an active path or with fewer than 30
ACTIVE pool questions at the test level is silently excluded. `NO_ELIGIBLE_SKILLS` includes the
business message for the case where weak skills existed but none remained after filtering.

### Assessment result composition

`GET /api/learning-paths/recommendation/by-assessment?assessmentAttemptId=<native ID>`
accepts the Assessment result's string ID (required, nonblank, at most 100 characters).
It resolves `attempts.assessment_source_attempt_id` for the authenticated user, with
`type=PERIODIC` and `status=COMPLETED`, then applies the same recommendation rules and response
as the ObjectId endpoint. Missing mappings and another owner's mappings return 404;
invalid query parameters return 400 and missing authentication returns 401.
Neither endpoint creates a path or changes the source results.

This adapter supports the imported Assessment/Learning Path projections. It reads only the
existing `attempts` and `micro_skill_results` projection, not `assessment_attempts`, and does
not create or synchronize projections for newly submitted assessments. A new Assessment
without that projection returns 404. Production delivery of those projections remains a
separate integration boundary. Clients must not guess ObjectIds or maintain ID maps.

The Assessment frontend displays the recommendation and opens a confirmation dialog before
creation. Native IDs use the create adapter below, rather than the ObjectId-only endpoint.

## Create

`POST /api/learning-paths` accepts:

```json
{
  "attemptId": "<ObjectId>",
  "microSkills": ["Listening for Gist", "Reading for Gist"]
}
```

Pass the complete `microSkills[].name` list from the latest `AVAILABLE` recommendation. The
server recalculates it. If it changed, it returns `409 ProblemDetail` with the current
`recommendation` property; request a new confirmation before retrying. On success, the shared
`ApiResponse` contains `{ "pathId": "...", "created": true }`. If that periodic test already
created a path, it returns the existing ID with `created: false`.

### Create from an Assessment result

`POST /api/learning-paths/by-assessment` accepts:

```json
{
  "assessmentAttemptId": "<native Assessment ID>",
  "microSkills": ["Inference & Author's View"]
}
```

The native ID is required, nonblank and at most 100 characters. The skill list must contain
1–5 nonblank names and match the full latest `AVAILABLE` recommendation. The adapter resolves
the authenticated user's completed periodic projection using the same lookup as Recommendation
and delegates to existing creation. Missing/foreign/incomplete projections return 404; invalid
bodies return 400 and unauthenticated requests return 401. A stale confirmation returns the
existing 409 response and current recommendation, without creating a path.

The response remains `{ "pathId": "...", "created": true|false }` in `ApiResponse`.
Sequential retries for an already-created source return the existing owned path. The frontend
sends POST only after the learner confirms, disables actions while the request is pending,
and navigates to `/learning-paths/:pathId?week=1` after success. Opening/cancelling the dialog
does not write data. On 409 it reloads Recommendation and requires a new confirmation.
As with Recommendation, newly submitted Assessments without an imported projection remain
unsupported. No projection synchronization or extra question content is added by this adapter.

Creation samples 30 distinct ACTIVE questions per eligible micro-skill, assigns 10 per week,
and builds all three weeks immediately. It saves immutable question snapshots first, then the
path, so creation works with the standalone MongoDB used by local Docker. These are separate
writes without a transaction. If a write fails, orphaned snapshots may remain; creation does not
clean them up automatically. The source test records currently lack `test_set_id` and `taken_at`,
so those optional source snapshot fields remain empty.
