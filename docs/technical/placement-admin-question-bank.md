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

For seed data, blank Listening URLs resolve from the provenance hash after
checking the WAV's bytes, encoding and duration. Reads leave the bank unchanged.
An explicit activation stores the canonical URL. New media selections must
reference the existing content-addressed WAV directory configured by
`engolearn.placement.media-directory`. The editor can select another WAV while
retaining the original provenance, and new published versions use the verified
duration of the selection. Keep WAVs referenced by published snapshots
available for historical attempts.
