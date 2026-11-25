# Learning Path passage management APIs

All five passage management APIs below are implemented. Validation, publication and
retirement endpoints will be agreed and implemented separately with the owner. Business scope: workspace `AGENT.md`, sections 17.8–17.11.

## Passage API sequence

Base: `/api/management/learning-paths/passages`.

| Order | Method | Path | Purpose | Status |
| --- | --- | --- | --- | --- |
| 1 | POST | base | Create a DRAFT passage, uploading audio for Listening | Implemented |
| 2 | GET | base | List with pagination and keyword/skill/level/status filters | Implemented |
| 3 | GET | `/{passageId}` | Read passage details and resolve audio for preview | Implemented |
| 4 | PATCH | `/{passageId}` | Edit a DRAFT passage | Implemented |
| 5 | DELETE | `/{passageId}` | Delete a DRAFT with no referencing questions | Implemented |

Follow with passage validation/publication/retirement, then question management.
Coverage dashboards and an operation-history screen are deferred business scope.

## POST: create a passage DRAFT

Requires a trusted authenticated cookie/session with CONTENT_MANAGER authority (ADMIN
inherits it). HTTP security protects the entire `/api/management/learning-paths` namespace;
`QuestionPoolPassageManagementService.create` independently enforces the same role through
Spring method security. Creator identity comes from CurrentUserProvider, never client data.

Request content type: `multipart/form-data` with these parts:

| Part | Content type | Meaning |
| --- | --- | --- |
| `passage` | `application/json` | Required typed metadata below |
| `audio` | MP3/WAV MIME type | Required, nonempty for LISTENING; absent for READING |

Metadata fields:

| Field | Rules |
| --- | --- |
| `skill` | Required: READING or LISTENING |
| `level` | Required: A1, A2, B1, B2, C1 or C2; same CEFR catalog as learner paths |
| `title` | Required, nonblank; maximum 200 characters, trimmed on save |
| `text` | Required and nonblank for READING; absent/blank for LISTENING; maximum 50,000 characters |
| `transcript` | Required and nonblank for LISTENING; absent/blank for READING; maximum 50,000 characters |

Limits are API input limits, not claims about CEFR content length. Audio is at most 25 MiB
(26,214,400 bytes). Allowed MIME types: audio/mpeg, audio/wav, audio/x-wav, audio/wave,
audio/vnd.wave. The Media contract also checks file signature, MIME consistency and actual
byte count; passing those checks does not establish audio quality or transcript accuracy.
Global multipart limits can be configured lower; see application.yaml.

Reading example (`reading.json`):

```json
{"skill":"READING","level":"B1","title":"Library hours","text":"The library opens at nine."}
```

```bash
curl --request POST http://localhost:8080/api/management/learning-paths/passages \
  --cookie 'ACCESS_TOKEN=<valid-access-cookie>' \
  --form 'passage=@reading.json;type=application/json'
```

Listening example (`listening.json`):

```json
{"skill":"LISTENING","level":"A2","title":"Bus timetable","transcript":"The bus arrives at ten."}
```

```bash
curl --request POST http://localhost:8080/api/management/learning-paths/passages \
  --cookie 'ACCESS_TOKEN=<valid-access-cookie>' \
  --form 'passage=@listening.json;type=application/json' \
  --form 'audio=@bus.mp3;type=audio/mpeg'
```

### Processing and response

Validate shape, authenticate/authorize and validate skill-specific content before upload or
passage insertion. For Listening, Learning Path calls `media.contract.MediaManagement`
with purpose LEARNING_PATH_PASSAGE_AUDIO and PROTECTED access. The controller owns and
closes the input stream. No file or provider calls for Reading.

Insert into `question_pool_passages`, generating server identifiers, forcing DRAFT and
revision 1, setting created_by and audit timestamps. There is no status/publication control
in the request. Return HTTP 201 and the shared ApiResponse envelope:

```json
{
  "data": {
    "passageId": "LP-PASS-<uuid>",
    "skill": "LISTENING",
    "level": "A2",
    "title": "Bus timetable",
    "text": null,
    "transcript": "The bus arrives at ten.",
    "audioMediaId": "<Media-owned UUID>",
    "status": "DRAFT",
    "revision": 1,
    "createdBy": "<trusted account ID>",
    "createdAt": "2026-11-25T05:00:00Z",
    "updatedAt": "2026-11-25T05:00:00Z"
  },
  "timestamp": "2026-11-25T05:00:00Z"
}
```

The response contains the stored media reference, not a provider URL. Authorized preview
URL resolution belongs to the detail API below; no generic public Media endpoint is added.
Creating from a question editor must preserve unsaved question data on the frontend; no
frontend implementation is part of this API change.

### Errors and failure ordering

Errors use ProblemDetail. A path-scoped `PassageMultipartExceptionResolver` handles the
413 case because multipart parsing can fail before MVC has selected a controller and its
scoped advice. It only handles POST to the passage collection and PATCH to a single passage; other controllers retain
their existing exception handling.

| Status | Code | Meaning |
| --- | --- | --- |
| 401 | AUTHENTICATION_REQUIRED | Missing/invalid trusted session |
| 403 | ACCESS_DENIED | Authenticated caller lacks management role |
| 400 | INVALID_PASSAGE_REQUEST | Invalid/missing JSON part, enum, field constraints or unreadable multipart stream |
| 400 | INVALID_PASSAGE | Invalid skill-specific content/audio, unsupported or mismatched audio |
| 413 | UPLOAD_TOO_LARGE | Servlet multipart size limit exceeded |
| 415 | UNSUPPORTED_MEDIA_TYPE | Wrong request/part content type |
| 503 | MEDIA_STORAGE_UNAVAILABLE | Media provider/persistence operation failed |
| 503 | PASSAGE_STORAGE_UNAVAILABLE | Passage insertion failed |

Upload failure does not insert a passage. Media upload precedes passage insertion; if the
latter fails, an unreferenced Media asset can remain. There is no cross-module transaction,
public Media delete or automatic orphan cleanup. Retrying POST can create another draft;
this API has no idempotency key. Do not retry silently or claim the two writes roll back together.

### Data compatibility and publication boundary

New passages keep the existing passage_id/skill/level/title/text/transcript/status/revision
fields, adding `audio_media_id` and `created_by`; `_id` and inherited timestamps use normal
Spring Data mappings. Existing documents are not rewritten or reset. Legacy `audio.url`
remains in existing passages. New DRAFT documents store a Media reference instead.

This create-only API cannot publish DRAFT content. Management preview resolution is implemented in the detail API below. Before adding
publication for uploaded Listening passages, implement Media-aware snapshot/practice delivery. Existing learner snapshot code expects legacy `audio.url`;
never manually mark these new Media-backed drafts ACTIVE. Existing paths/snapshots,
recommendation and learner progress are untouched by passage creation.

## GET: list passages

`GET /api/management/learning-paths/passages` returns HTTP 200 in the shared ApiResponse
envelope. Both the existing namespace security rule and the Spring-managed list service
require CONTENT_MANAGER authority, inherited by ADMIN. Managers can list the content pool
across creators; this is not a learner-owned resource list.

| Query parameter | Rules |
| --- | --- |
| `keyword` | Optional, maximum 200 characters; trimmed, blank means no keyword filter |
| `skill` | Optional: READING or LISTENING |
| `level` | Optional: A1, A2, B1, B2, C1 or C2 |
| `status` | Optional: DRAFT, ACTIVE or RETIRED |
| `page` | Zero-based integer, minimum 0; omitted/blank defaults to 0 |
| `size` | Integer 1–100; omitted/blank defaults to 20 |

Omitted filters impose no restriction; unfiltered lists include all statuses. Supplied
filters combine with AND. Keyword matches a substring of title OR Reading text OR
Listening transcript, case-insensitively. It is a literal string, not a client regex;
regex metacharacters are escaped. Accent folding, stemming and relevance ranking are not
implemented.

Sort is fixed: updated_at descending, then _id descending for ties. Missing legacy timestamps
are left as null and follow MongoDB's normal sort behavior. Offset pagination returns an
empty items array for a page beyond the last page while preserving filtered totals. No
matches returns items=[], totalElements=0 and totalPages=0. Count and page reads use the
same filter but are separate database reads; concurrent edits can affect consistency across
requests. No transaction or fixed result snapshot is promised.

```http
GET /api/management/learning-paths/passages?keyword=bus&skill=LISTENING&level=A2&status=DRAFT&page=0&size=20
```

Example response:

```json
{
  "data": {
    "items": [{
      "passageId": "LP-PASS-<uuid>",
      "title": "Bus timetable",
      "skill": "LISTENING",
      "level": "A2",
      "status": "DRAFT",
      "revision": 1,
      "hasAudio": true,
      "createdAt": "2026-11-25T05:00:00Z",
      "updatedAt": "2026-11-25T05:00:00Z"
    }],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-11-25T05:00:00Z"
}
```

Items are summaries: full text, transcript, creator, media ID and audio URLs are omitted.
`hasAudio` is true when either audio_media_id or legacy audio.url is nonblank; it describes
a stored reference, not file availability or editorial approval. Listing never calls Media
to upload or resolve a URL. The model now maps the existing legacy audio.url field for
reading only; existing documents and schema are not migrated or rewritten. The database
page query projects summary fields instead of loading full passage text/transcripts.

Invalid enums, noninteger pagination, out-of-range values and an overlong keyword return
400 ProblemDetail with code INVALID_PASSAGE_REQUEST. The existing 401/403 contracts apply.
Database failures return 503 PASSAGE_STORAGE_UNAVAILABLE. This API adds no index or database
reset. Literal substring searches over content may scan the collection; assess real dataset
performance before introducing an index/search strategy.

## GET: passage detail and audio preview

`GET /api/management/learning-paths/passages/{passageId}` reads by the exact `passage_id`
value returned by create/list, not MongoDB `_id`. IDs are opaque, nonblank and at most 100
characters. The existing namespace policy and the Spring-managed detail service require
CONTENT_MANAGER authority (ADMIN inherits it), before passage lookup or Media resolution.
Managers may inspect passages created by other editors and all DRAFT/ACTIVE/RETIRED states.

Return HTTP 200 with `Cache-Control: no-store` and ApiResponse containing the full passage:

```json
{
  "data": {
    "passageId": "LP-PASS-<uuid>",
    "skill": "LISTENING",
    "level": "A2",
    "title": "Bus timetable",
    "text": null,
    "transcript": "The bus arrives at ten.",
    "audioMediaId": "<Media-owned UUID>",
    "audio": {
      "url": "https://<provider>/<temporary-audio-url>",
      "expiresAt": "2026-11-25T05:01:00Z",
      "contentType": "audio/mpeg",
      "durationSec": null,
      "format": null
    },
    "status": "DRAFT",
    "revision": 1,
    "createdBy": "<trusted account ID>",
    "createdAt": "2026-11-25T05:00:00Z",
    "updatedAt": "2026-11-25T05:00:00Z"
  },
  "timestamp": "2026-11-25T05:00:00Z"
}
```

This is a distinct response DTO; the create response and list summaries are unchanged.
Audio resolution follows the stored passage, never a media identifier supplied separately
by the browser:

- A nonblank audio_media_id takes precedence. After management authorization, call the
  public MediaManagement.resolve capability and return its URL, expiresAt and contentType.
  The Media-owned protected URL lifetime is configured there (default 60 seconds). To
  obtain another URL after expiry, refetch this same authorized detail endpoint. URLs are
  returned in the response only, never written into the passage or snapshot.
- Without a media reference, return a nonblank legacy audio.url unchanged, including relative
  URLs, and preserve stored audio.duration_sec/audio.format. expiresAt and contentType are
  null because the legacy document does not provide them. This does not guarantee the URL
  is available or has no external expiry.
- Without either reference, audio is null. Incomplete imported drafts can still be inspected;
  this endpoint neither invents content nor publishes it.

No duration/format is fabricated for new Media assets; those fields are null because the
current MediaDeliveryDto does not expose them. A stale new reference fails explicitly,
even if a legacy URL also exists, rather than silently playing different content.

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_PASSAGE_REQUEST | Blank or overlong passage ID |
| 401 | AUTHENTICATION_REQUIRED | Missing/invalid trusted session |
| 403 | ACCESS_DENIED | Authenticated caller lacks management authority |
| 404 | PASSAGE_NOT_FOUND | No passage with this passage_id |
| 404 | MEDIA_NOT_FOUND | The passage's Media reference no longer exists |
| 503 | MEDIA_STORAGE_UNAVAILABLE | Media cannot produce a delivery URL |
| 503 | PASSAGE_STORAGE_UNAVAILABLE | Passage lookup failed |

Errors are ProblemDetail and omit provider/database diagnostics. Resolving a URL does not
check actual playback or transcript accuracy. Provider playback remains a manual integration
check. This API performs no content, revision, audit timestamp, status or learner-state writes.

## PATCH: edit a DRAFT passage

`PATCH /api/management/learning-paths/passages/{passageId}` requires the same trusted
CONTENT_MANAGER authority (including ADMIN) at both HTTP and service entry points. ID
validation matches detail reads. Content type is multipart/form-data with required JSON
`passage` and optional `audio` file. Return HTTP 200 and the same metadata response shape
as create, with the new revision and updatedAt; use GET detail for a preview URL.

| JSON field | Meaning |
| --- | --- |
| `revision` | Required integer >= 1; revision last read by the editor |
| `skill`, `level`, `title`, `text`, `transcript` | Optional partial changes; enums/length limits match create |

An omitted or null optional field keeps its stored value. Supply at least one nonnull
field or an audio file in addition to revision. A supplied blank title is invalid. Empty
text/transcript may only clear fields irrelevant to the resulting skill; required content
must remain nonblank. State, IDs, creator and timestamps are server-controlled, not editable
through this request.

```bash
curl --request PATCH http://localhost:8080/api/management/learning-paths/passages/LP-PASS-example \
  --cookie 'ACCESS_TOKEN=<valid-access-cookie>' \
  --form 'passage={"revision":1,"title":"Updated title"};type=application/json'
```

To replace Listening audio, send JSON `{"revision":1}` (plus any metadata changes) and
`audio=@replacement.mp3;type=audio/mpeg` in the same request. Upload limits, supported MIME
types and Media signature validation match creation. An explicitly sent empty file is an
error, not a request to retain/delete audio.

The merged result must have valid content: Reading requires text and accepts no new audio
or nonblank transcript; Listening requires transcript and either an existing audio
reference or a valid replacement file. No audio part retains the current new/legacy audio
reference without resolving a delivery URL. Changing Reading to Listening requires the
resulting transcript and an audio reference/file; changing Listening to Reading requires
text and removes old transcript/audio references. Irrelevant old content is cleared when
changing skill, rather than copied into the new stimulus type. Assets are not deleted.

When skill or level changes, inspect all Question Pool records referencing passage_id,
regardless of question status. Reject the change if a referencing question would have a
different/missing skill or level from the target passage. This also rejects incomplete
reference data rather than guessing its classification. Editing title/content without
changing classification leaves question documents unchanged.

Read and validate DRAFT status/revision, content and references before upload. Save with an
atomic Mongo predicate on passage_id + DRAFT + expected revision. Increment revision once,
update updated_at/updated_by from the server, retain created_at/created_by and preserve
unmapped fields. Existing legacy audio subdocuments are untouched when retaining audio;
replacement unsets legacy audio and stores the new audio_media_id. There is no full-document
replacement or write to learner paths, snapshots, results or questions.

Both an initially stale revision/non-DRAFT status and a concurrent edit/publication that
wins before the conditional write return 409 PASSAGE_UPDATE_CONFLICT. Refetch the passage
and let the editor reconcile it; do not automatically retry with a newer revision. Missing
passages return 404 PASSAGE_NOT_FOUND. Invalid metadata/content/audio returns 400, multipart
limit violations 413, wrong content type 415, and Media/database failures 503 using the
existing codes above. Incompatible question references also return 409 PASSAGE_UPDATE_CONFLICT.

Media upload precedes the conditional write. Upload failure preserves the original passage.
If upload succeeds but the database write fails or loses the revision race, an unreferenced
new Media asset may remain; the original passage reference is retained. Replaced assets
also remain in Media because its public contract has no delete/reference cleanup capability.
The API does not claim an atomic transaction spanning Media and Learning Path.

Reference validation and passage update are separate operations; no lock/transaction with
future question authoring is introduced. When question create/edit/publication APIs are
implemented they must enforce passage classification/status and coordinate concurrent
reference changes. This endpoint alone cannot guarantee cross-document invariants against
out-of-band database writers.

## DELETE: remove an unreferenced DRAFT passage

`DELETE /api/management/learning-paths/passages/{passageId}?revision=<current revision>`
requires CONTENT_MANAGER authority (inherited by ADMIN), independently enforced at the
HTTP namespace and the Spring-managed delete service. The caller is obtained from the
trusted CurrentUserProvider. This is a management operation across editors, not creator-only
access. The ID is the opaque passage_id, nonblank and at most 100 characters.

The `revision` query parameter is required and must be an integer >= 1. Use the revision
returned by list/detail/create/update; this protects an editor's delete action from removing
content changed since it was viewed. There is no request body.

```bash
curl --request DELETE \
  'http://localhost:8080/api/management/learning-paths/passages/LP-PASS-example?revision=1' \
  --cookie 'ACCESS_TOKEN=<valid-access-cookie>'
```

Processing:

1. Authorize the management operation and load the passage by passage_id.
2. Require DRAFT and the expected revision.
3. Check question_pool for any passage_id reference, regardless of question status,
   skill/level validity or whether that question is currently selectable.
4. Atomically remove one passage with a predicate on passage_id + DRAFT + expected revision.
   A concurrent edit/publication/removal that invalidates this predicate returns a conflict.
5. Return HTTP 200 with the shared ApiResponse envelope:

```json
{
  "data": {"passageId": "LP-PASS-example", "deleted": true},
  "timestamp": "2026-11-25T05:00:00Z"
}
```

Only the passage document is removed. No Media upload/resolve/delete call is made; existing
Media asset metadata/files and legacy URLs are not deleted. Existing learner paths,
question snapshots, results and Question Pool questions are not modified. This is physical
DRAFT deletion, not retirement of ACTIVE content and not a soft-delete workflow.

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | INVALID_PASSAGE_REQUEST | Missing/noninteger/nonpositive revision, or invalid ID |
| 401 | AUTHENTICATION_REQUIRED | No valid trusted session |
| 403 | ACCESS_DENIED | Authenticated caller lacks management authority |
| 404 | PASSAGE_NOT_FOUND | Passage absent at the initial lookup |
| 409 | PASSAGE_DELETE_CONFLICT | Not DRAFT, stale revision, remaining question references or failed conditional delete |
| 503 | PASSAGE_STORAGE_UNAVAILABLE | Passage/reference lookup or delete failed |

Errors use ProblemDetail. After a 409, refetch/reconcile instead of automatically retrying
with the latest revision. A later DELETE after a completed deletion returns 404. If two
requests both read the same DRAFT before removal, only one conditional delete can succeed;
the losing request returns 409.

Reference checking and conditional removal are separate operations, with no cross-document
transaction/lock. As with PATCH, future question-authoring operations must coordinate
reference changes with passage revision/lifecycle. This endpoint does not guarantee against
out-of-band question inserts between the reference check and removal. No automatic Media
cleanup or operation-history screen is introduced.

## Verification scope

Run Learning Path tests only, per the owner's explicit instruction. The passage integration
tests use real HTTP/session authorization, Spring service proxies, MongoDB Testcontainers
and Media's real signature/metadata processing. Only the external Media storage provider
is replaced by a test double; actual Cloudinary delivery and human audio review remain manual.
