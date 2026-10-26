# Start a Learning Path practice day

`POST /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/start?userId={userId}`
returns the shared `{ data, timestamp }` envelope. `pathId` and `userId` are MongoDB
ObjectIds; `weekNo` is 1–3 and `dayNo` is 1–5. The path must belong to the user,
the week must be open, and the day must be available before its deadlines.

On the first successful request, the day becomes `IN_PROGRESS`. The server stores
`started_at`, the `yyyy-MM-dd` date in `Asia/Ho_Chi_Minh`, and `auto_close_at` at
the next local midnight. The path becomes `IN_PROGRESS` if it was `NOT_STARTED`.
The update sets `next_run_at` to the earlier of its current value and the new day deadline.
These fields are updated conditionally so concurrent first requests cannot restart
the day. A repeat request while the day is still open returns the original times
and the same ten questions in their saved order.

The response `data` contains `pathId`, `weekNo`, `dayNo`, `status`, `startedAt`,
`autoCloseAt`, and `questions`. Each question contains `order`, `questionId`,
`microSkill`, `skill`, `prompt`, four `{ optionId, text }` options, and an optional
`passage` with `title`, `text`, and optional `audio { url, durationSec, format }`.
Content is read from the immutable `learning_path_questions` snapshots, never
from the mutable question pool. The response type has no correct option,
explanation, or transcript field.

Errors use `ProblemDetail`: malformed parameters return 400; a path not owned by
the user or a nonexistent week/day returns 404; a locked, closed, completed,
cancelled, or expired day returns 409. Missing or inconsistent saved question
snapshots are server errors and do not start a new day.

This endpoint only begins or reopens a day and returns questions. Saving answer
drafts, submitting, and viewing the detailed day result need separate APIs.
The frontend practice screen is still a local preview until it is connected.
