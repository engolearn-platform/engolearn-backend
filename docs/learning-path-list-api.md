# Learning Path list API

Base path: `/api/learning-paths`

## List

`GET /api/learning-paths?userId=<ObjectId>&status=<status>` returns list items for the requested
status. `status` defaults to `ACTIVE`; the supported values are `ACTIVE`, `COMPLETED`, and
`CANCELLED`. The response also includes total counts for all three sections.

## Search

`GET /api/learning-paths/search?userId=<ObjectId>&keyword=<keyword>` searches only the current
user's active (`NOT_STARTED`, `IN_PROGRESS`) and completed paths. It matches either an exact
creation date in `DD/MM/YYYY` format or a case-insensitive partial micro-skill name. Cancelled
paths are excluded, and the search response retains the same list envelope and unfiltered section
counts.

Both endpoints return the shared `ApiResponse` envelope. Search results are ordered by creation
time descending and use the same list item DTO as the list endpoint.

## Recommendation

`GET /api/learning-paths/recommendation?userId=<ObjectId>&attemptId=<ObjectId>` reads a
completed periodic test (`attempts.type=PERIODIC`, `status=COMPLETED`), its valid
`micro_skill_results`, active paths, and the question pool. It does not write data. The result
contains `state`, `level`, `durationWeeks=3`, `daysPerWeek`, `questionsPerDay=10`, and
`microSkills` with the test scores. Possible states are `AVAILABLE`, `NO_WEAK_SKILLS`,
`NO_ELIGIBLE_SKILLS`, and `ALREADY_CREATED`; the last includes `existingPathId`.

Only results below 50% qualify. A micro-skill already in an active path or with fewer than 30
ACTIVE pool questions at the test level is silently excluded. `NO_ELIGIBLE_SKILLS` includes the
business message for the case where weak skills existed but none remained after filtering.

## Create

`POST /api/learning-paths` accepts:

```json
{
  "userId": "<ObjectId>",
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
and builds all three weeks immediately. The path and immutable question snapshots are saved in
one MongoDB transaction. MongoDB must run as a replica set or sharded cluster and
`engolearn.mongodb.transactions.enabled=true` must be configured. Until then, creation returns
`503`; recommendation remains available. The existing local Docker setup is standalone and
requires conversion before a successful local POST. The source test records currently lack
`test_set_id` and `taken_at`, so those optional source snapshot fields remain empty.
