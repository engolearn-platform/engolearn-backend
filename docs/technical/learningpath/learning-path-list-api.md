# Learning Path list API

Base path: `/api/learning-paths`.

All endpoints require the authenticated access cookie. The backend takes the owner ID from
the session; clients do not send a user ID. Missing/invalid sessions return 401. Foreign or
missing paths and Assessment sources return 404.

## User ID storage

`user_id` is an opaque BSON string in `learning_paths` and `learning_path_questions`, including
IDs such as `pfc-demo-learner-v2`. Path `_id` and question snapshot `path_id` remain BSON
ObjectIds. Assessment source and paper IDs are strings. Fresh mixed datasets use explicit
Extended JSON ObjectIds with automatic string conversion disabled; see the
[importer instructions](../../../tools/mongo-json-importer/README.md#datasets-with-string-user-ids).

## Assessment source

Learning Path consumes `assessment.contract.AssessmentResults`, never Assessment repositories
or collections. `findCompleted(userId, assessmentAttemptId)` returns the exact owner's stored
READY result, including all five measured skills independently of display grants. A newly
submitted READY Assessment is immediately usable; no legacy projection import or background
synchronization is needed. Reads do not grade/settle attempts, consume quota, or grant access.
Demo results remain restricted to `dev & !prod` by Assessment.

The code no longer reads `attempts` or `micro_skill_results`. A native Assessment ID must
never be replaced with the old projection ObjectId. Both current and alias endpoints below
now interpret their source IDs as native Assessment IDs, nonblank and at most 100 characters.
This changes the former ObjectId-only endpoints' input meaning; callers must use native IDs.

The current published Assessment codes map to the existing pool names as follows. The L03
mapping was explicitly confirmed by the user; Vietnamese display labels are not lookup keys.

| Assessment code | Learning Path pool micro-skill |
| --- | --- |
| R01 | Reading for Specific Information |
| R02 | Reading for Gist |
| R04 | Inference & Author's View |
| L01 | Listening for Gist |
| L03 | Listening for Specific Information |

Only these five Assessment codes currently have an agreed mapping; the pool's broader catalog
does not define codes for Assessment. Unknown codes, duplicate/incomplete measured skills,
missing level/band, invalid counts or inconsistent percentages return 409, without guessing
an equivalent skill. Learning Path reads the current profile level through
`user.contract.UserLevelLookup` for pool eligibility and creation. Missing profile/level
returns 409 for a new recommendation. Existing paths return their stored level without
requiring a current profile. Assessment level remains source metadata.

## List and search

`GET /api/learning-paths?status=<status>` defaults to `ACTIVE`; other supported values are
`COMPLETED` and `CANCELLED`. Counts for all three sections are returned in the same envelope.

`GET /api/learning-paths/search?keyword=<keyword>` searches the owner's active and completed
paths by exact `DD/MM/YYYY` creation date or partial case-insensitive micro-skill name.
Cancelled paths are excluded. Results are distinct and ordered by creation time descending.

Items retain `sourceAssessmentAttemptId: string|null`. The source is stored directly in
`learning_paths.source.assessment_attempt_id`. One owner-scoped batch call to
`findCompletedAttemptIds` checks all distinct source IDs; missing, foreign, unfinished,
hidden demo and blank sources return null. There is no per-card query or projection lookup.
Paths with no native source remain usable according to their practice state, with no result link.

The frontend link remains `/assessment/attempts/:attemptId/result`. That page calls the
existing Assessment result HTTP API, which owns result visibility and analysis grants.
The internal contract does not bypass the frontend's full-analysis access policy.

## Recommendation

`GET /api/learning-paths/recommendation/by-assessment?assessmentAttemptId=<native ID>`
is the endpoint used by the Assessment frontend. The alias
`GET /api/learning-paths/recommendation?attemptId=<native ID>` applies identical rules.

Recommendation is read-only. It returns `state`, `level`, `durationWeeks=3`, `daysPerWeek`,
`questionsPerDay=10`, and `microSkills`. States remain `AVAILABLE`, `NO_WEAK_SKILLS`,
`NO_ELIGIBLE_SKILLS`, and `ALREADY_CREATED` (including `existingPathId`).

Only skills below 50% qualify. An occupied skill in an active path or one with fewer than
30 ACTIVE pool questions at the current user level is excluded. Recommendation does not
sample questions, write snapshots, reserve skills or start a schedule. Returned score/band
values come from Assessment; band names are `NEEDS_PRACTICE`, `MONITOR`, `MEETS_THRESHOLD`.

## Create

The current endpoint is `POST /api/learning-paths/by-assessment`:

```json
{
  "assessmentAttemptId": "<native Assessment ID>",
  "microSkills": ["Inference & Author's View"],
  "level": "B1"
}
```

The alias `POST /api/learning-paths` accepts the same native source under the field
`attemptId`. Both require 1–5 nonblank skill names matching the complete latest AVAILABLE
recommendation, plus a required `level` exactly equal to the recommendation the learner
confirmed. Accepted values are A1, A2, B1, B2, C1 and C2; missing/null/blank or unsupported
values return 400. Creation rereads the source and recalculates eligibility. A stale confirmation
returns `409 ProblemDetail` with the current `recommendation`; refresh and reconfirm before retrying.
This includes a changed current user level even when the eligible skill names are unchanged.
Missing/foreign/non-READY sources return 404, invalid bodies 400 and unauthenticated requests 401.

The response remains `{ pathId, created: true|false }` in the shared `ApiResponse` envelope.
An existing source returns its same owned path with `created=false`, even after completion or
cancellation. Source IDs use Assessment's returned canonical representation, including when
the native Mongo ID is an ObjectId. A unique partial index on the native source prevents
concurrent duplicate creation; the active owner/micro-skill unique index prevents overlaps.
Indexes are declared on `LearningPath` and initialized by `LearningPathIndexConfig`.

Creation freezes the recalculated recommendation level, samples 30 distinct ACTIVE questions
at that level per eligible skill and builds all three weeks. Later profile changes do not
change existing paths or their question snapshots.

The supplied level is a confirmation value, not an override: UserLevelLookup remains
authoritative through the recalculated recommendation. A mismatch is rejected before any
question selection or snapshot/path writes. A matching request freezes the recalculated
level. Once an owned path already exists for the source, a well-formed request still returns
that same path with created=false, regardless of a changed or missing current profile.
There is no cross-module User lock: the level is checked at the recommendation read that
decides creation; later profile changes do not rewrite the plan.

Compatibility: both create endpoints now require level. Existing frontend callers that
send only the source and skills receive 400 until updated to submit the level captured at
dialog opening, show it during confirmation and refresh/reconfirm after 409. This backend
change does not update the frontend itself. No persistence migration is needed.

It saves question snapshots first, then the path, without a cross-module transaction, so
standalone local MongoDB can still create a path from an existing completed result. Failed
or concurrent losing writes may leave orphaned snapshots; they are never used by the winning
path. Automatic orphan cleanup is not implemented.

The path freezes its source `assessment_attempt_id`, `paper_id`, completion time and baseline
scores. Baselines identify each measured skill with `skill_code`, together with the source
Assessment ID; there is no separate `micro_skill_results.result_id` reference. Practice and
lifecycle grading continue reading immutable Learning Path question snapshots.

The frontend still opens a confirmation dialog, sends POST only after confirmation, disables
actions during creation and navigates to `/learning-paths/:pathId?week=1` on success.

## Level confirmation verification — 2026-11-26

- `./mvnw.cmd "-Dtest=LearningPath*,QuestionPoolPassageManagementIntegrationTest,SecurityIntegrationTest#*LearningPath*+learningPath*" "-Dsurefire.useManifestOnlyJar=false" clean verify`:
  BUILD SUCCESS; 301 tests, zero failures/errors/skips. The launcher option avoids
  the known Windows manifest classpath issue on this workspace without changing tests.
- Real HTTP/MongoDB coverage exercises both create endpoints: identical skills
  with A2-to-B1 level change return 409/current B1 recommendation before writes;
  reconfirming B1 creates B1 path/snapshots while preserving the source Assessment;
  missing/invalid levels and malformed JSON return 400; existing source idempotency
  survives subsequent profile changes/removal. A level change with no eligible
  pool returns the current NO_ELIGIBLE_SKILLS recommendation without writes.
- 33 Learning Path security cases passed; standalone Mongo creation and existing
  practice/lifecycle/passage regressions also passed. `git diff --check` passed.
- Frontend integration was not changed or verified here. Its create payload must
  be updated as described above before testing the full confirmation dialog flow.
