# Learning Path question management — reads

Implemented scope: list and detail only. Create/edit/delete/copy/readiness/publish/retire/replace
remain planned in the [workspace business entry](../../../../AGENT.md), section 17.12.
Passage CRUD and audio preview are owned by the [passage contract](learning-path-management-api-plan.md).

## Access and persistence

Base: `/api/management/learning-paths/questions`. Both GET operations require CONTENT_MANAGER,
including inherited ADMIN. The existing explicit management HTTP matcher enforces 401/403;
each service entry also uses `@PreAuthorize` through its Spring proxy. Responses use
`ApiResponse<T>` and `Cache-Control: no-store`; errors use `application/problem+json`.
These responses contain editorial content and must not be reused as learner endpoints.

The repository reads existing `question_pool` documents using `question_id`, not Mongo `_id`.
The typed read model preserves stored snake_case names, including `options[].option_id`.
Canonical micro-skill names remain strings; no new catalog, collection, index or migration
is introduced. Missing optional audit data or revision stays null; reads do not invent it.
There are no writes to questions, passages, Media assets, paths, snapshots, drafts or results.

## GET list

`GET /api/management/learning-paths/questions`

| Query | Meaning |
| --- | --- |
| `keyword` | Up to 200 characters; literal case-insensitive substring of question ID or prompt |
| `skill` | `READING` or `LISTENING` |
| `microSkill` | Up to 200 characters; exact case-sensitive canonical name |
| `level` | `A1`, `A2`, `B1`, `B2`, `C1`, `C2` |
| `status` | `DRAFT`, `ACTIVE`, `RETIRED`; omitted includes all states |
| `passageId` | Up to 100 characters; exact public passage ID, including dangling references |
| `page` | Zero-based, default 0, nonnegative int |
| `size` | Default 20; 1–100 |

Filters combine with AND. Keyword, microSkill and passageId are stripped; blank values
are ignored. Unknown canonical micro-skill names return no matches rather than require a
hard-coded catalog. Regex symbols in keyword are literal text. Sorting is `updated_at DESC,
_id DESC` for stable ties. Count and page are separate reads, not a transactional snapshot;
totals can change under concurrent authoring. No unrequested client-controlled sort is accepted.

HTTP 200:

```json
{
  "data": {
    "items": [{
      "questionId": "LP-Q-R-GIST-B1-001",
      "skill": "READING",
      "microSkill": "Reading for Gist",
      "level": "B1",
      "passageId": null,
      "prompt": "What is the main idea?",
      "status": "DRAFT",
      "revision": 1,
      "createdAt": "2026-11-26T01:00:00Z",
      "updatedAt": "2026-11-26T01:00:00Z"
    }],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Empty/no-match/out-of-range pages return 200 with empty items. Out-of-range pages retain
matching totals. The list neither fetches passage/Media content nor returns options,
correct answers or explanations. Global status counters and coverage dashboards are not
part of these two APIs. Prompt is returned as stored, without truncation.

## GET detail

`GET /api/management/learning-paths/questions/{questionId}`

The ID is required, nonblank, at most 100 characters and matched exactly. All three
question states are readable. `data` contains all list item fields plus:

- `options`: stored order of `{ "optionId": "A", "text": "..." }` values;
- `correctOptionId`, `explanation` for admin review only;
- `createdBy`, `updatedBy` where present;
- `passage`: the existing typed passage detail/preview response, or null.

The read does not enforce publication eligibility: incomplete DRAFTs remain inspectable,
and no missing prompt, options, answer, revision or passage is synthesized. Normally an
authored question has four choices A–D, but this GET returns stored choices without
repairing or approving them. Reading with no passage returns null. A nonblank passageId
that references a missing passage is retained with `passage: null`, allowing editors to
see the broken reference. A missing question itself returns 404 `QUESTION_NOT_FOUND`.

An existing passage is returned even if DRAFT, RETIRED or mismatched: preview is not readiness.
It includes Reading text or Listening transcript/audio. Detail reuses the authorized
passage service. Nonblank `audio_media_id` takes priority and resolves fresh protected
Media delivery with expiry/content type; missing Media returns 404 and unavailable Media
returns 503, with no legacy fallback. Otherwise legacy audio URLs pass through unchanged.
Generated URLs are response-only and never saved. No URL is fetched, file uploaded,
Cloudinary delivery policy changed or media playback asserted. Learner Media delivery
support remains unchanged; this API does not remove its publication blocker.

## Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_QUESTION_REQUEST | Invalid query, enum, pagination or ID shape |
| 401 | AUTHENTICATION_REQUIRED | No trusted session |
| 403 | ACCESS_DENIED | Caller lacks management authority |
| 404 | QUESTION_NOT_FOUND | No matching public question ID |
| 404 | MEDIA_NOT_FOUND | Passage references a missing Media asset |
| 503 | QUESTION_STORAGE_UNAVAILABLE | Question, passage or Media database access failed |
| 503 | MEDIA_STORAGE_UNAVAILABLE | Protected delivery resolution unavailable |

Infrastructure errors are not converted to empty lists, dangling references or successful
previews. Existing model conversion limitations for malformed imported enum/field types
are unchanged; these reads are not a general raw-data repair API.

## Verification

`QuestionPoolQuestionManagementIntegrationTest` uses real HTTP sessions, Spring service
proxies and MongoDB Testcontainers; only the Media public contract is doubled to exercise
preview resolution/failures without external uploads. It covers filters, literal search,
pagination, answer visibility, legacy field mapping, incomplete drafts, missing references,
permission denial/inheritance, storage failures and unchanged source/learner data.
The [passage integration suite](learning-path-management-api-plan.md#verification-scope)
covers the underlying Media integration separately. Live Cloudinary upload and frontend
integration are outside this backend-read-only task.

Verification on 2026-11-26:

- Targeted question management suite: 25 tests passed, no failures/errors/skips.
- Final `./mvnw.cmd "-Dtest=LearningPath*,QuestionPool*ManagementIntegrationTest" "-Dsurefire.useManifestOnlyJar=false" verify`:
  BUILD SUCCESS; 293 tests passed, no failures/errors/skips, including existing passage
  and learner regression suites. The Surefire option uses the working Windows classpath
  invocation; it does not disable tests. Log: `target/question-management-verification.log`.
- Verification is scoped to Learning Path per the existing owner instruction; the
  repository-wide suite and manual frontend/live Cloudinary checks were not run.
