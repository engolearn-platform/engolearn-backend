# Learning Path list API

Base path: `/api/learning-paths`

All endpoints require an authenticated access cookie. The backend uses the session's user ID
for list, search, recommendation, creation, detail, and practice operations. Clients do not
send a `userId`. Missing or invalid sessions return 401; a path or periodic test owned by
another user is not returned.

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

The Assessment frontend displays the recommendation and a disabled “Tạo lộ trình học tập”
button for UI review. Creation is not connected to this adapter; its native ID must not be
passed to the existing ObjectId-only create endpoint.

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

Creation samples 30 distinct ACTIVE questions per eligible micro-skill, assigns 10 per week,
and builds all three weeks immediately. It saves immutable question snapshots first, then the
path, so creation works with the standalone MongoDB used by local Docker. These are separate
writes without a transaction. If a write fails, orphaned snapshots may remain; creation does not
clean them up automatically. The source test records currently lack `test_set_id` and `taken_at`,
so those optional source snapshot fields remain empty.
