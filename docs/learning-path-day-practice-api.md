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
It also contains `selectedOptionId`, which is null until a draft is saved and
restores the latest selection when an in-progress day is reopened.
Content is read from the immutable `learning_path_questions` snapshots, never
from the mutable question pool. The response type has no correct option,
explanation, or transcript field.

Errors use `ProblemDetail`: malformed parameters return 400; a path not owned by
the user or a nonexistent week/day returns 404; a locked, closed, completed,
cancelled, or expired day returns 409. Missing or inconsistent saved question
snapshots are server errors and do not start a new day.

## Save or change one answer draft

`PUT /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/questions/{questionId}/draft?userId={userId}`
accepts `{ "selectedOptionId": "A" }`, where the option is one of A–D and must
exist in that question's saved snapshot. Each successful call replaces the
selection for that question and returns `questionId`, `selectedOptionId`, and
`savedAt` in the shared response envelope. A repeat call can change the answer.
The server saves the draft inside the Learning Path, updates the local activity
date and inactivity cancellation deadline, and recalculates `next_run_at`.
It does not grade the draft or return the correct option.

The path must belong to the user; the week and day must still be open and the
day must already be in progress. A question that has been finalized or a day
whose deadline has passed cannot be edited. Unknown path/week/day/question
returns 404, invalid option returns 400, and a closed or expired state returns
409. The atomic update also checks the saved deadline and item state to guard
against concurrent closure.

## Submit a practice day

`POST /api/learning-paths/{pathId}/weeks/{weekNo}/days/{dayNo}/submit?userId={userId}`
requires no request body. The path must belong to the user, the week must be open,
and the day must already be in progress and before its deadline. The server
finalizes and grades the saved drafts atomically. Blank questions remain unanswered;
a fully answered day becomes `COMPLETED`, otherwise it becomes `CLOSED` with
close reason `SUBMITTED`. The response envelope contains `pathId`, `weekNo`,
`dayNo`, `status`, `closedAt`, `assigned`, `answered`, `correct`, `wrong`,
`completionRate`, and `practiceScore` (null when no answers were saved).

The endpoint returns 404 for an unknown path, week, or day and 409 when the
day cannot be submitted, including a repeat submission or an elapsed deadline.
Weeks 1 and 2 wait until midnight before closing; if all questions in week 3
have been answered, the final submission closes the week and path immediately.
Submission, draft saves and the lifecycle job use a path revision to detect
concurrent writes; older documents without a revision are supported. Detailed
question review still requires a separate result API.
