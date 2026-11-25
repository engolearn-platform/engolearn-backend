# Learning Path passage management APIs

The first API below is implemented. The remaining APIs are planned and will be implemented
one at a time with the owner. Business scope: workspace `AGENT.md`, sections 17.8–17.11.

## Passage API sequence

Base: `/api/management/learning-paths/passages`.

| Order | Method | Path | Purpose | Status |
| --- | --- | --- | --- | --- |
| 1 | POST | base | Create a DRAFT passage, uploading audio for Listening | Implemented |
| 2 | GET | base | List with pagination and keyword/skill/level/status filters | Planned |
| 3 | GET | `/{passageId}` | Read passage details and resolve audio for preview | Planned |
| 4 | PATCH | `/{passageId}` | Edit a DRAFT passage | Planned |
| 5 | DELETE | `/{passageId}` | Delete a DRAFT with no referencing questions | Planned |

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
URL resolution belongs to the planned detail API; no generic public Media endpoint is added.
Creating from a question editor must preserve unsaved question data on the frontend; no
frontend implementation is part of this API change.

### Errors and failure ordering

Errors use ProblemDetail. A path-scoped `PassageMultipartExceptionResolver` handles the
413 case because multipart parsing can fail before MVC has selected a controller and its
scoped advice. It only handles POST to this passage endpoint; other controllers retain
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

This create-only API cannot publish DRAFT content. Before adding publication for uploaded
Listening passages, implement authorized Media resolution in management previews and
snapshot/practice delivery. Existing learner snapshot code expects legacy `audio.url`;
never manually mark these new Media-backed drafts ACTIVE. Existing paths/snapshots,
recommendation and learner progress are untouched by passage creation.

## Verification scope

Run Learning Path tests only, per the owner's explicit instruction. The passage integration
tests use real HTTP/session authorization, Spring service proxies, MongoDB Testcontainers
and Media's real signature/metadata processing. Only the external Media storage provider
is replaced by a test double; actual Cloudinary delivery and human audio review remain manual.
