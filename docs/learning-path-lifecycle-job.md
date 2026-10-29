# Learning Path lifecycle job

The scheduler scans active paths at every minute boundary in `Asia/Ho_Chi_Minh` and once when the application starts. It selects `is_active = true` documents with `next_run_at <= now` using the `{ is_active, next_run_at }` index. A failed scan is retried on the next run; one malformed path does not stop the remaining paths.

For each path, overdue deadlines are processed in chronological order. At the same deadline the order is: close started days, close/open the week or finish the path, then check inactivity. The stored `closed_at`, `opened_at`, `ended_at`, and `cancel.at` are the business deadline, not the later time at which a recovery run happened.

- A started day closes at midnight after its `started_date`. Its saved drafts are finalized against the immutable question snapshots before day result, progress, and week statistics are computed. Questions without drafts remain unanswered. A day that has not started closes with zero answers when its week closes.
- Weeks 1 and 2 close at the first midnight after all days are terminal, or at their seven-day deadline. The next week opens at the same instant. Opening a week does not change a path from `NOT_STARTED` to `IN_PROGRESS`.
- Week 3 closes at its seven-day deadline if still active. A submission that completes all questions in week 3 closes the week and path immediately.
- A path with no saved answer draft for 14 whole local calendar days after its activity anchor is cancelled at midnight on day `L+15`. Week 3 completion takes precedence if its deadline coincides with cancellation.

`next_run_at` is the earliest of an in-progress day's `auto_close_at`, the open week's deadline (including an early close at the next midnight), and `inactivity.cancel_at`. Path creation and the day-start API set this field when they introduce a deadline; saving an answer draft refreshes the activity anchor and this scheduled deadline. The day-start and draft APIs check the week, inactivity, and day deadlines before writing, including when the scheduler has not yet updated the document.

The job updates only the affected fields in `learning_paths`, preserving other document fields. Its MongoDB update checks the expected path status, answer count, `next_run_at` and revision. Start, draft, submission and lifecycle updates increment that revision, so concurrent writes retry against current data. Existing documents without a revision are treated as the initial version.

`LearningPath` inherits `BaseDocument`: its Java ID is a string, while MongoDB `_id` and question snapshot `path_id` remain BSON ObjectIds. `created_at` remains a MongoDB date. The inherited `updated_at` may be populated by Spring auditing when an entity is saved; the direct MongoDB updates used by practice and lifecycle do not guarantee that it changes on every operation.
