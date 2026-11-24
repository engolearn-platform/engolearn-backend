# Placement Admin question bank

The question bank is available in Mongo persistence mode to admins with the
application's normal credentials. Success responses use the shared `ApiResponse`
envelope. Validation errors use `ProblemDetail` and can include an error map keyed
by form field or import row.

| Method and path under `/api/v1/placement/admin/questions` | Behavior |
| --- | --- |
| `GET /` | Search by code, prompt or passage with optional `skill`, `level` and `status` filters. The `page` starts at zero and `size` is 1–100. |
| `GET /{id}` | Read content, options, correct option, explanation, transcript, status, revision and playable media. |
| `POST /` | Create a draft. |
| `PUT /{id}` | Edit question content at the supplied `revision`. |
| `PATCH /{id}/status` | Activate or deactivate a question with `status` and `revision`. |
| `POST /import` | Validate and insert 1–500 drafts in a Mongo transaction using `{ "questions": [...] }`. |
| `GET /media` | List verified existing WAV URLs, durations and provenance titles when available. |

An existing question without a revision starts at zero. Each edit or status
transition increments the revision. Question codes are trimmed and uppercased,
and a case-insensitive unique index prevents duplicates. Check existing codes
for case-insensitive duplicates before deploying against a different database.
Import requires a Mongo replica set to guarantee the batch rolls back together.

Listening uses the shared Media contract. ADMIN may POST multipart PCM WAV to `/api/v1/placement/admin/questions/media`; the backend validates the bytes/duration and stores a content-hash journal/reference before returning mediaId and playable URL. GET `/media/preflight` reports missing journal assets; POST `/media/migrate` links imported assets explicitly. No automatic upload/migration runs at startup.

Cloudinary credentials stay on BE. Placement listening media is PUBLIC for Guest playback; upload/link actions still require ADMIN. `placement_audio_assets` links content hashes to shared `media_assets`. Retain media referenced by historical snapshots. Dev can also validate the packaged hash-named WAV directory; prod must use durable Media references, not filesystem URLs. See [Media integration](../media/integration-guide.md), [runtime](placement-local-runtime.md) and [current seed](../../../tools/mongo-json-importer/samples/placement/README.md).
