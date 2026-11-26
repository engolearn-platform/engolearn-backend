# Learning Path question management

Implemented scope: list, detail, create DRAFT, edit DRAFT, delete DRAFT and copy to DRAFT.
Readiness/publish/retire/replace remain planned in the [workspace business entry](../../../../AGENT.md), section 17.12.
Passage CRUD and audio preview are owned by the [passage contract](learning-path-management-api-plan.md).

## Access and persistence

Base: `/api/management/learning-paths/questions`. All six operations require CONTENT_MANAGER,
including inherited ADMIN. The existing explicit management HTTP matcher enforces 401/403;
each service entry also uses `@PreAuthorize` through its Spring proxy. Responses use
`ApiResponse<T>` and `Cache-Control: no-store`; errors use `application/problem+json`.
These responses contain editorial content and must not be reused as learner endpoints.

The repository accesses existing `question_pool` documents using `question_id`, not Mongo `_id`.
The typed read model preserves stored snake_case names, including `options[].option_id`.
Canonical micro-skill names remain strings; no new catalog, collection, index or migration
is introduced. Missing optional audit data or revision stays null; reads do not invent it.
GETs have no writes. Mutations affect source questions and, when attaching a passage,
its reference-fencing revision/audit only. No Media asset, learner path, assigned snapshot,
answer draft or result is changed. No endpoint uploads or deletes files.

Question mutations require enabled MongoDB transactions and a replica set/sharded topology.
Both supported runtime profiles already enable the transaction manager. A missing manager
or transaction infrastructure failure returns 503; this implementation has no automatic
nontransactional fallback. List/detail remain available without a transaction manager.

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
| 400 | INVALID_QUESTION | Invalid merged question content or passage reference |
| 401 | AUTHENTICATION_REQUIRED | No trusted session |
| 403 | ACCESS_DENIED | Caller lacks management authority |
| 404 | QUESTION_NOT_FOUND | No matching public question ID |
| 404 | MEDIA_NOT_FOUND | Passage references a missing Media asset |
| 409 | QUESTION_WRITE_CONFLICT | State/revision conflict, exhausted revision or concurrent source change |
| 415 | UNSUPPORTED_MEDIA_TYPE | JSON mutation body requires a supported JSON content type |
| 503 | QUESTION_STORAGE_UNAVAILABLE | Question, passage or Media database access failed |
| 503 | MEDIA_STORAGE_UNAVAILABLE | Protected delivery resolution unavailable |

Infrastructure errors are not converted to empty lists, dangling references or successful
previews. Existing model conversion limitations for malformed imported enum/field types
are unchanged; these reads are not a general raw-data repair API.

## POST create a DRAFT

`POST /api/management/learning-paths/questions` with `Content-Type: application/json`:

```json
{
  "skill": "READING",
  "microSkill": "Reading for Gist",
  "level": "B1",
  "passageId": null,
  "prompt": "The library closes at five. When does it close?",
  "options": [
    {"optionId": "A", "text": "At three."},
    {"optionId": "B", "text": "At five."},
    {"optionId": "C", "text": "At seven."},
    {"optionId": "D", "text": "At nine."}
  ],
  "correctOptionId": "B",
  "explanation": "The sentence states that it closes at five."
}
```

Required: skill, canonical microSkill, level, nonblank prompt/explanation, exactly four
non-null choices with distinct IDs A–D and one correctOptionId A–D. Each choice text is
nonblank and at most 10,000 characters; prompt/explanation at most 50,000; microSkill at
most 200; passageId at most 100. Text is stripped before storing. The options' order is
preserved; blank passageId becomes absent. Listening requires a passage ID; Reading does
not. A referenced passage must exist and match skill/level. DRAFT, ACTIVE and RETIRED
passages are inspectable/selectable while authoring a DRAFT; publication will require
ACTIVE. Draft authoring neither resolves Media nor asserts readiness/playback/audio quality.

Create/edit recognize the ten canonical names and their Reading/Listening ownership in
the workspace business entry, section 3. This is an application-level mapping, not a new
DB catalog; adding a canonical skill currently requires updating the mapping. Unknown
names and translated labels are rejected. Exact canonical case is retained after stripping.
Distinct choice text, editorial evidence, professional review and Media-aware learner
delivery are publication concerns deferred to readiness/publish, not claimed by saving DRAFT.

Server generates a new `LP-Q-<uuid>` public ID and Mongo ID, forces DRAFT and revision 1.
Creator/updater come from CurrentUserProvider and timestamps from the server. The body
does not grant control over identity, audit, revision, status or publication approval.
HTTP 201 returns:

```json
{"data":{"questionId":"LP-Q-<uuid>","status":"DRAFT","revision":1,
"createdBy":"<authenticated account>","updatedBy":"<authenticated account>",
"createdAt":"2026-11-26T16:00:00Z","updatedAt":"2026-11-26T16:00:00Z"}}
```

## PATCH edit a DRAFT

`PATCH /api/management/learning-paths/questions/{questionId}` with JSON:

```json
{"revision":1,"prompt":"Updated question","passageId":""}
```

Revision is a required positive int. Only the exact current DRAFT revision is editable;
ACTIVE/RETIRED, stale/missing stored revisions, exhausted int revision and a lost CAS
return 409. Initially absent questions return 404. Supply at least one content field.
Other fields are optional and null/omitted means unchanged. Send `passageId: ""` to detach
a Reading passage; detaching Listening without changing it to Reading is rejected.
The complete merged content must pass the create checks. Replace options as a complete
four-choice list; correctOptionId may change independently. Invalid metadata creates no
partial edit. A single conditional update increments revision, sets trusted updated_by/at
and preserves ID, creator, created_at and unowned imported fields. HTTP 200 returns the
same mutation shape as create with the saved revision/audit. There is no automatic retry.

## DELETE a DRAFT

`DELETE /api/management/learning-paths/questions/{questionId}?revision=1`

Requires a positive int revision and a current DRAFT. Delete physically removes only that
source question under the status/revision predicate. It does not remove the passage or
Media, or search/modify assigned learner snapshots. Stale revision or non-DRAFT returns
409; initially absent and a repeated successful deletion return 404. A lost conditional
delete returns 409. HTTP 200: `{"data":{"questionId":"LP-Q-...","deleted":true}}`.

## POST copy to a new DRAFT

`POST /api/management/learning-paths/questions/{questionId}/copy` with JSON:

```json
{"revision":3}
```

Requires a positive source revision. The source may be DRAFT, ACTIVE or RETIRED. Missing
source returns 404; a revision mismatch at the transaction snapshot read returns 409.
Copy creates a fresh ID, DRAFT/revision 1 and trusted current audit. It copies only owned
content fields/options/reference, not source audit, arbitrary imported metadata or
publication_review. The source question is never rewritten or retired. Copied content
must pass the same structural checks as create/edit; invalid imported sources are not
exempt and no missing choices or answers are synthesized. An existing reference must still resolve to a
matching, versioned passage; missing/mismatched references are rejected rather than
silently removed. A missing Listening reference blocks copying. Repair a DRAFT source
first or author a corrected new question. Copy is not approval or publication.

The source revision identifies the snapshot being copied; a later concurrent source edit
does not change that snapshot or force a write to the source. The passage is shared, not
duplicated or uploaded. HTTP 201 returns the create mutation shape. Repeating the request
creates another DRAFT: copy/create are not idempotent.

## Passage reference consistency

Create/edit/copy with a nonblank passage ID read current metadata and perform a conditional
write matching its public ID + revision + skill + level + recognized status, incrementing
passage revision and setting updated_by/at. The question write and passage fence share
one Mongo transaction. Publication review, content, audio and lifecycle status of the
passage are preserved. An invalid/nonpositive/exhausted passage revision returns 409.
The revision is an aggregate concurrency token, not just a content version; even an ACTIVE
or RETIRED passage's revision may increase when a DRAFT reference is attached.

Existing passage edit/delete/publish/retire predicates use the same revision. A concurrent
classification edit or deletion cannot both win against attaching an incompatible/dangling
reference. Losing either write rolls back the other; transaction write conflicts return
409 and require refetching. Storage/commit uncertainty returns 503, not success. Refetch
before retrying any uncertain mutation; do not blindly repeat create/copy.

Removing a reference or deleting a question does not bump the old passage revision: this
cannot introduce an incompatible reference. Reference counts may reject a simultaneous
passage operation conservatively until its next retry. Out-of-band writes/imports that
ignore revision guards remain outside this guarantee. These DRAFT operations do not solve
ACTIVE-reference publication/retirement races; future question publish/replace must join
this coordination before becoming available.

## Verification

`QuestionPoolQuestionManagementIntegrationTest` uses real HTTP sessions, Spring service
proxies and MongoDB Testcontainers; only the Media public contract is doubled to exercise
preview resolution/failures without external uploads. It covers filters, literal search,
pagination, answer visibility, legacy field mapping, incomplete drafts, missing references,
permission denial/inheritance, authoring validation, draft immutability guards, copy audit,
transaction rollback, concurrent question edits, passage deletion/classification races
and unchanged learner data.
The [passage integration suite](learning-path-management-api-plan.md#verification-scope)
covers the underlying Media integration separately. Live Cloudinary upload and frontend
integration are outside this backend task.

Verification on 2026-11-26:

- Initial authoring suite: 56 tests passed, no failures/errors/skips; the final run below
  includes additional passage-race and option-mapping cases, totaling 59 question tests.
- Final `./mvnw.cmd "-Dtest=LearningPath*,QuestionPool*ManagementIntegrationTest" "-Dsurefire.useManifestOnlyJar=false" verify`:
  BUILD SUCCESS; 327 tests passed, no failures/errors/skips, including existing passage
  and learner regression suites. The Surefire option uses the working Windows classpath
  invocation; it does not disable tests. Log: `target/question-authoring-verification.log`.
- Configured workspace DB preflight: hello reported replica-set topology with writable
  primary/session support; a read-only transaction completed and was aborted. No live
  business data was written. Rollback/write-race checks use only the disposable test DB.
- Verification is scoped to Learning Path per the existing owner instruction; the
  repository-wide suite and manual frontend/live Cloudinary checks were not run.
