# Learning Path list API

Base path: `/api/learning-paths`

## List

`GET /api/learning-paths?userId=<ObjectId>&status=<status>` returns list items for the requested
status. `status` defaults to `ACTIVE`; the supported values are `ACTIVE`, `COMPLETED`, and
`CANCELLED`. The response also includes total counts for all three sections.

## Search

`GET /api/learning-paths/search?userId=<ObjectId>&keyword=<keyword>` searches only the current
user's active (`NOT_STARTED`, `IN_PROGRESS`) and completed paths. It matches either an exact
creation date in `DD/MM/YYYY` format or a case-insensitive partial micro-skill name. Cancelled
paths are excluded, and the search response retains the same list envelope and unfiltered section
counts.

Both endpoints return the shared `ApiResponse` envelope. Search results are ordered by creation
time descending and use the same list item DTO as the list endpoint.
