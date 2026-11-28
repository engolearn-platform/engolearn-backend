# Learning Path question management

Implemented scope: all ten backend APIs — list, detail, create/edit/delete DRAFT, copy,
readiness, publish, retire and replace. Business rules are in the
[workspace business entry](../../../../AGENT.md), section 17.12. Section 17.13 tracks the
learner Media delivery and the remaining local-audio migration. The temporary
capability blocker was removed after [live verification](learning-path-media-verification.md).
Passage CRUD and audio preview are owned by the [passage contract](learning-path-management-api-plan.md).

## Access and persistence

Base: `/api/management/learning-paths/questions`. All ten operations require CONTENT_MANAGER,
including inherited ADMIN. The existing explicit management HTTP matcher enforces 401/403;
each service entry also uses `@PreAuthorize` through its Spring proxy. Responses use
`ApiResponse<T>` and `Cache-Control: no-store`; errors use `application/problem+json`.
These responses contain editorial content and must not be reused as learner endpoints.

The repository accesses existing `question_pool` documents using `question_id`, not Mongo `_id`.
The typed read model preserves stored snake_case names, including `options[].option_id`.
Canonical micro-skill names remain strings. `question_pool_coverage_fences` adds a technical
concurrency record per micro-skill/level; no catalog/content migration is introduced.
Missing optional audit data or revision stays null; reads do not invent it.
GETs have no writes. Mutations affect source questions and, when attaching a passage,
its reference-fencing revision/audit and the technical coverage fence. No Media asset, learner path, assigned snapshot,
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
- `publicationReview`: saved source revision, passage ID/reviewed revision, confirmed
  checklist and trusted reviewer/time, or null for content without a recorded review.

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
| 409 | QUESTION_COVERAGE_CONFIRMATION_REQUIRED | Explicit admin confirmation required; includes `confirmationRequired` and typed `warnings` |
| 422 | QUESTION_NOT_READY | Automated blockers; includes current typed `readiness` extension |
| 422 | QUESTION_REVIEW_REQUIRED | Missing or unrelated confirmations; includes current typed `readiness` |
| 415 | UNSUPPORTED_MEDIA_TYPE | JSON mutation body requires a supported JSON content type |
| 503 | QUESTION_STORAGE_UNAVAILABLE | Question, passage or Media database access failed |
| 503 | MEDIA_STORAGE_UNAVAILABLE | Protected delivery resolution unavailable |

Infrastructure errors are not converted to empty lists, dangling references or successful
previews. Existing list/detail model conversion limitations for malformed imported
enum/field types are unchanged. The diagnostic readiness read below handles malformed
classification/option fields explicitly; it does not repair them.

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
ignore revision guards remain outside this guarantee. Question publication and replacement
join the ACTIVE-only guard below.

## GET publication readiness

`GET /api/management/learning-paths/questions/{questionId}/readiness`

An explicit, read-only evaluation of saved data, with the same ID constraints as detail.
It returns 200 even when automated checks fail. Missing question returns 404. Reads use
a diagnostic projection with raw classification strings and field-type diagnostics,
avoiding enum conversion failures on malformed imported drafts. No repair, review,
reservation, revision increment or upload occurs.

```json
{"data":{
  "questionId":"LP-Q-<uuid>","revision":1,"status":"DRAFT","structurallyValid":true,
  "passageId":null,"passageRevision":null,"blockers":[],"warnings":[],
  "requiredReviewChecks":["MICRO_SKILL_APPROPRIATE","LEVEL_APPROPRIATE",
    "SINGLE_BEST_ANSWER_WITH_EVIDENCE","DISTRACTORS_PLAUSIBLE","EXPLANATION_SUPPORTED",
    "STIMULUS_SUFFICIENT","NO_ANSWER_LEAKAGE"]
}}
```

Every issue is `{ "code": "PROMPT_REQUIRED", "field": "prompt", "message": "..." }`.
`structurallyValid` means automated blockers are empty, not that the question is approved
or can be published without review. Answer/choices/explanation, transcript and delivery
URLs are not returned here. Use detail for preview. Status/revision reflect saved data;
missing values are not invented. Incomplete or non-DRAFT records remain diagnosable.

Automated checks:

- Known scalar fields have appropriate types; status is DRAFT and revision is positive,
  integral and below the int increment limit. Skill is Reading/Listening, canonical
  micro-skill belongs to it, and level is A1–C2.
- Prompt/explanation are nonblank and at most 50,000 characters. Exactly four choices,
  each ID A–D appearing once, nonblank text up to 10,000 and a correctOptionId A–D present
  in the choices. Obvious duplicate texts after stripping, whitespace collapse and
  case folding block publication; this does not detect all semantic equivalents.
- Listening requires passage; Reading may omit it. A referenced passage exists, is
  ACTIVE and matches skill/level. The existing passage content/audio checks are reused
  with ACTIVE status required; unrelated incompatible questions do not invalidate this
  question's own reference. All current passage content/media blockers still apply.
- A passage must have a usable positive revision below the int limit for the publication
  fence. An observed revision/status change between advisory reads is `PASSAGE_CHANGED`.

Question blocker codes: `INVALID_STORED_FIELD`, `QUESTION_NOT_DRAFT`, `INVALID_REVISION`,
`REVISION_EXHAUSTED`, `INVALID_SKILL`, `INVALID_MICRO_SKILL`, `INVALID_LEVEL`,
`PROMPT_REQUIRED`, `EXPLANATION_REQUIRED`, `CONTENT_TOO_LONG`, `OPTIONS_COUNT_INVALID`,
`OPTION_IDS_INVALID`, `OPTION_TEXT_REQUIRED`, `OPTION_TEXTS_DUPLICATE`,
`CORRECT_OPTION_INVALID`, `PASSAGE_REQUIRED`, `PASSAGE_ID_INVALID`, `PASSAGE_NOT_FOUND`,
`PASSAGE_CLASSIFICATION_MISMATCH`, `PASSAGE_REVISION_EXHAUSTED`, `PASSAGE_CHANGED`.
Reused passage issues retain their existing code and prefix field paths with `passage.`;
these include `PASSAGE_NOT_ACTIVE`, invalid/missing text, transcript and audio.

Media-backed Listening is publishable with valid delivery and all required review
confirmations. The temporary learner-delivery blocker was removed after
[live verification](learning-path-media-verification.md). Backend snapshot/start/result
delivery and renewal are described in the [learner audio contract](learning-path-day-practice-api.md#snapshot-audio-and-delivery-renewal).
Never silently fall back to the older file. Missing Media
is an automated `AUDIO_MEDIA_NOT_FOUND` blocker. Unavailable storage/database fails the
request with 503, not a misleading content failure. Legacy usable URLs retain
`LEGACY_AUDIO_PLAYBACK_UNVERIFIED`: the server does not fetch arbitrary URLs or assert
successful playback. Human listening review is still required.

Required professional confirmations (seven Reading; all nine Listening):

| Check | Human attestation |
| --- | --- |
| MICRO_SKILL_APPROPRIATE | The question measures the intended canonical skill |
| LEVEL_APPROPRIATE | Source, prompt, choices and required processing fit the level |
| SINGLE_BEST_ANSWER_WITH_EVIDENCE | Exactly one best answer has clear supporting evidence |
| DISTRACTORS_PLAUSIBLE | Incorrect choices are plausible, intentional and unsupported |
| EXPLANATION_SUPPORTED | Explanation identifies evidence/reasoning for the answer |
| STIMULUS_SUFFICIENT | The provided source/context is sufficient without outside knowledge |
| NO_ANSWER_LEAKAGE | Stimulus/prompt/choices do not accidentally disclose the answer |
| AUDIO_MATCHES_TRANSCRIPT | Listening recording matches the transcript |
| AUDIO_QUALITY_REVIEWED | Recording was heard and reviewed for quality/level |

There is no minimum-30 or coverage requirement for a single publication. Advisory reads
are separate operations, not a fixed transaction snapshot. A later edit invalidates a
displayed result; the frontend must refetch/reconfirm instead of automatically publishing.

## POST publish a DRAFT

`POST /api/management/learning-paths/questions/{questionId}/publish` with JSON:

```json
{"revision":1,"confirmedReviewChecks":[
  "MICRO_SKILL_APPROPRIATE","LEVEL_APPROPRIATE","SINGLE_BEST_ANSWER_WITH_EVIDENCE",
  "DISTRACTORS_PLAUSIBLE","EXPLANATION_SUPPORTED","STIMULUS_SUFFICIENT","NO_ANSWER_LEAKAGE"
]}
```

Revision is a required positive int. Confirmations are a required set (at most nine),
with no null/unknown enum values. Missing/malformed body is 400. Empty/partial sets or
Listening-only checks supplied for Reading are 422 `QUESTION_REVIEW_REQUIRED`, not a
saved partial approval. Confirm exactly the current readiness checklist.

The service uses one Mongo transaction, without automatic retry:

1. Read current diagnostic question metadata; require DRAFT and exact request revision.
   Initial absence is 404; non-DRAFT, stale revision or exhausted increment is 409 before
   Media resolution. The previous GET readiness is neither required nor trusted.
2. Re-evaluate readiness inside the transaction and reject automated blockers with 422
   `QUESTION_NOT_READY`. Require the exact professional confirmations for this revision.
3. Fence the micro-skill/level cohort as described below. When linked, conditionally
   fence the passage by public ID + evaluated revision +
   skill + level + ACTIVE. Increment its revision and set updated_by/at, preserving
   content, audio, status and existing publication review. A concurrent passage
   retirement/edit cannot win together with an invalid new ACTIVE reference.
4. Atomically update question matching ID + DRAFT + expected revision: set ACTIVE,
   increment revision, update trusted audit and save publication_review. Either both
   question and passage writes commit or both roll back. Known concurrent write failures
   return 409; infrastructure/uncertain commit outcomes remain 503 and require refetching.

The embedded review stores `source_revision`, optional `passage_id`/`passage_revision`,
`confirmed_checks`, `reviewed_by`, `reviewed_at`. Passage revision is the value evaluated
before the reference fence increments it. Confirmations use canonical checklist order.
Reviewer comes from the session and time from the server; no second reviewer is required.
Existing imported ACTIVE questions can have no recorded review; no approval is fabricated.
Professional confirmation is an attestation, not automated CEFR/playback certification.

HTTP 200 returns `questionId`, `status`, saved `revision`, `reviewedRevision`, optional
`passageId`/`reviewedPassageRevision`, `publishedBy`, `publishedAt`, `confirmedReviewChecks`
and readiness `warnings`. The review is also visible in detail's `publicationReview`.
Reading without a passage can publish with no passage write. Warnings alone do not block
after required confirmations. A replay returns 409 and preserves the original review.
Content is then immutable under existing PATCH/DELETE guards; copying still creates DRAFT
without inheriting the approval. Publication does not publish a passage, select new learner
questions or rewrite any assigned snapshot, answer, score, draft or path progress.

Retirement's reference count and its final passage update remain separate reads/writes,
but publication fences the same passage revision. If retirement counted zero before a
publication commits, its stale CAS fails. If retirement wins first, publication rolls
back. Out-of-band writers/imports that bypass these guards remain unsupported.

## POST retire an ACTIVE question

`POST /api/management/learning-paths/questions/{questionId}/retire` with JSON:

```json
{"revision":3,"confirmCoverageWarning":false}
```

Revision is required/positive. Optional acknowledgement defaults false (including null).
Require ACTIVE and exact current revision: initially missing is 404; non-ACTIVE, stale
or exhausted revision is 409. Scalar public ID is required. Retirement does not require
valid question content, usable passage/audio or Media resolution; defective content can
be removed from future selection. It preserves content, creator, created_at, publication
review and unknown metadata, and never retires/deletes its passage or learner data.

The transaction fences the cohort, counts ACTIVE before, conditionally retires/increments
revision/updates trusted audit and counts after. Counts use the same micro-skill + level
+ ACTIVE predicate as path eligibility. If after-count is below 30 and acknowledgement
is false, the transaction rolls back before returning 409. Example ProblemDetail extensions:

```json
{"code":"QUESTION_COVERAGE_CONFIRMATION_REQUIRED","confirmationRequired":true,
"warnings":[{"code":"BELOW_MINIMUM_ACTIVE_QUESTIONS","message":"...",
"coverage":{"microSkill":"Reading for Gist","level":"B1",
"activeQuestionCountBefore":30,"activeQuestionCountAfter":29,
"minimumRequired":30,"eligibleAfter":false}}]}
```

Display this warning; only after admin confirmation resend with `confirmCoverageWarning: true`.
The backend rechecks state/revision and recomputes counts. Confirmation accepts
below-minimum coverage, not an exact count reservation. 31 → 30 needs no shortage
acknowledgement; 29 → 28 still requires it. No partial source/audit/fence increment is saved
on the rejected attempt. False/default never becomes automatic approval.

HTTP 200 returns questionId, RETIRED status, saved revision, retiredBy/retiredAt, `coverage`
and `warnings`; warnings remain after confirmation. Unclassifiable imported micro-skill
or CEFR level yields null counts/eligibility with `COVERAGE_UNDETERMINED`, requiring the
same acknowledgement rather than fabricating zero. Raw conditional retirement avoids
enum/content conversion failures for defective imports. Replay returns 409 unchanged.

## POST replace an ACTIVE question

`POST /api/management/learning-paths/questions/{questionId}/replace` targets the old ACTIVE:

```json
{"revision":3,"replacementQuestionId":"LP-Q-<draft-id>","replacementRevision":1,
"confirmedReviewChecks":["MICRO_SKILL_APPROPRIATE","LEVEL_APPROPRIATE",
"SINGLE_BEST_ANSWER_WITH_EVIDENCE","DISTRACTORS_PLAUSIBLE","EXPLANATION_SUPPORTED",
"STIMULUS_SUFFICIENT","NO_ANSWER_LEAKAGE"],"confirmCoverageWarning":false}
```

Both revisions are required positive ints. Replacement ID is nonblank/max-100, different
from source ID. Require current old ACTIVE and new DRAFT revisions; missing either is
404, stale/wrong state/exhausted revision is 409, self replacement is 400. Replacement
uses the same fresh readiness/exact professional confirmations as publish; 422 includes
the replacement readiness. Media-backed Listening remains blocked.

One transaction fences the affected cohorts, counts before, publishes the replacement
with its ACTIVE-only passage fence/review, conditionally retires old, then counts after.
All question, passage and cohort writes commit or roll back together; no two-request
publish/retire sequence or automatic retry. Both transitions share trusted actor/time.
Old review/content remain intact; no assigned snapshot, answer, result, draft or progress
is changed. No unused passage is automatically retired.

Replacement may correct micro-skill/level. Same-cohort replacement has no net decrease,
so needs no shortage acknowledgement; existing below-30 coverage is an informational
warning. Across cohorts, decreased coverage ending below 30 or unknown old coverage
requires the same explicit acknowledgement and rollback-before-confirmation contract.
The gaining cohort staying below 30 is informational, not a publication minimum.

HTTP 200 returns `retiredQuestion` (ID, RETIRED status, revision, retiredBy/At),
`publishedQuestion` (the publish receipt, including review/audio warnings), `coverage`
(one entry for same cohort, otherwise old then replacement cohort), and coverage `warnings`.
Completed replay conflicts; after uncertain outcomes refetch both questions before retrying.

## Concurrent coverage coordination

Publish, retire and replace all fence a micro-skill/CEFR pair in
`question_pool_coverage_fences`, inside the mutation transaction. Model-owned bootstrap
creates the collection; neutral revision-zero records are initialized before the
transaction by deterministic unique `_id` with
micro_skill, level, revision and audit timestamps. It stores no counts: question_pool
remains authoritative. Existing content needs no reset/reseed/migration. Restart/deploy
updated backend to register the model; implementation did not write live DB data.

Question Pool transactions request SNAPSHOT read concern and MAJORITY write concern via
Spring Data Mongo labels scoped to this capability. Neutral metadata allocation may remain
after a rejected mutation; it stores no approval/count and leaves source state unchanged.
Shared manager defaults/other features are unchanged. Neutral initialization is idempotent;
two-pair fences use deterministic order. Transaction/CAS conflicts return 409, requiring
explicit refetch/retry. For example, two retirements seeing 31 cannot
both silently leave 29: one wins and the other conflicts; its next attempt sees 30 → 29
and requires confirmation. Publish also joins this coordination.

Counts describe this serialized operation, not a reservation against later changes or
all path eligibility conditions. Precise pair coordination needs identifiable metadata;
malformed classification returns an explicit undetermined warning. All active-state
writers must use the guard; direct imports/DB writes and mixed deployments with older
writers remain outside this guarantee. Never reset/delete fences during active writes.

## Verification

`QuestionPoolQuestionManagementIntegrationTest`, `QuestionPoolQuestionPublicationIntegrationTest`
and `QuestionPoolQuestionLifecycleIntegrationTest`
use real HTTP sessions, Spring service proxies and MongoDB Testcontainers;
only the Media public contract is doubled to exercise
preview resolution/failures without external uploads. It covers filters, literal search,
pagination, answer visibility, legacy field mapping, incomplete drafts, missing references,
permission denial/inheritance, authoring validation, draft immutability guards, copy audit,
transaction rollback, concurrent question edits, passage deletion/classification races
and unchanged learner data. Publication coverage also verifies raw malformed diagnostics,
current re-evaluation, exact professional confirmations, recorded review/audit, replay and
concurrent publication, Media delivery blockers/failures and both outcomes of the race
with passage retirement (including a stale zero-reference count). Lifecycle coverage
also verifies explicit shortage acknowledgement, unknown imported classifications,
atomic replacement/rollback, same/different-cohort counts, first-use cohort allocation,
concurrent retirement/publication/replacement and preserved Media capability blockers.
The [passage integration suite](learning-path-management-api-plan.md#verification-scope)
covers the underlying Media integration separately. Live Cloudinary upload and frontend
integration are outside this backend task.

Verification on 2026-11-27:

- Targeted lifecycle suite after the concurrency fix: 30 tests passed with no
  failures/errors/skips. The final run includes an additional replacement Media case.
- Final `./mvnw.cmd "-Dtest=LearningPath*,QuestionPool*IntegrationTest" "-Dsurefire.useManifestOnlyJar=false" verify`:
  BUILD SUCCESS; 402 tests passed, no failures/errors/skips, including 59 question
  authoring/read tests, 44 publication tests, 31 lifecycle tests and existing passage
  and learner regression suites. The Surefire option uses the working Windows classpath
  invocation; it does not disable tests. Log: `target/question-lifecycle-verification.log`.
- Earlier workspace DB preflight (2026-11-26): hello reported replica-set topology with writable
  primary/session support; a read-only transaction completed and was aborted. No live
  business data was written. Rollback/write-race checks use only the disposable test DB.
- Verification is scoped to Learning Path per the existing owner instruction; the
  repository-wide suite and manual frontend/live Cloudinary checks were not run.

## Shared micro-skill catalog

Authoring and publication validate active definitions through `microskill.contract`; list/detail responses add `microSkillCatalog`. Canonical request fields remain compatible. [Catalog and deletion protection](../microskill/README.md).
