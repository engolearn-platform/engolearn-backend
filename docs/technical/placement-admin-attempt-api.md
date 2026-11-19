# Admin Placement attempt lookup

Only users with the `ADMIN` role can call the attempt lookup endpoints. Responses use the backend's `ApiResponse` envelope and include `Cache-Control: no-store`.

| Method and path | Payload in `data` |
| --- | --- |
| `GET /api/v1/placement/admin/attempts` | `items`, `total`, `page` and `size`. Rows contain subject, assigned test-set version, start time, status, stored result and claim status. |
| `GET /api/v1/placement/admin/attempts/{id}` | One attempt, including `deadlineAt`, `endedAt`, ordered `snapshot` and saved selections in `answers` keyed by question ID. Returns 404 if the attempt ID is unknown. |

The list combines optional filters `search`, `userId`, `testSetCode`, `version`, `status`, `level`, `from` and `to`. The search field matches the exact attempt ID or User ID. Test-set code matches case-insensitively. `from` and `to` are inclusive ISO dates in `Asia/Ho_Chi_Minh`, applied to `startedAt`. Page numbers are zero-based and page sizes range from 1 to 100. The default page is 0 with size 100. Ordering is by latest start time, then ascending attempt ID.

The assigned test-set ID and version identify the snapshot, including after archive. If that version is unavailable, the test-set ID is used for display, `totalQuestions` is null and the detail still includes recorded answers. For `IN_PROGRESS` and `EXPIRED`, the result fields are null and the snapshot's answer keys, explanations and transcripts are blank. A saved zero is returned as zero for a scored attempt. Guest display references come from the attempt ID. Claimed attempts show the associated User ID and `ALREADY_CLAIMED` status.

Lookup reads the stored attempt and version. It does not resolve deadlines, claim results or update a learner profile.
