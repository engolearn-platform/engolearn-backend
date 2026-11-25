# AI Companion HTTP contract

All endpoints require `ROLE_USER` and disable caching. Missing and foreign-owned conversation IDs return the same 404 response.

## Conversations

Base path: `/api/v1/companion/conversations`.

| Method and path | Response |
| --- | --- |
| `POST /api/v1/companion/conversations` | `201 ApiResponse<ConversationResponse>`; creates an empty conversation. |
| `GET /api/v1/companion/conversations?before=<cursor>&limit=20` | `200 ApiResponse<ConversationPageResponse>`; cursor order is `updatedAt` descending, then ID descending. `limit` is 1–100. |
| `GET /api/v1/companion/conversations/{conversationId}` | `200 ApiResponse<ConversationResponse>`. |
| `DELETE /api/v1/companion/conversations/{conversationId}` | `200 ApiResponse<Void>`; removes the conversation and its messages. |
| `GET /api/v1/companion/conversations/{conversationId}/messages?beforeSequence=<n>&limit=20` | `200 ApiResponse<MessagePageResponse>`; the first page is newest, each page is returned in ascending sequence order. |

`ConversationResponse` contains `id`, nullable `title`, `createdAt`, `updatedAt`, `messageCount`, `maxMessages`, and `warningThreshold`. `ConversationPageResponse` contains `items` and nullable `nextCursor`. The cursor is base64url of the last row's timestamp and ID.

Each `MessageResponse` contains `id`, `sequence`, `role` (`USER` or `ASSISTANT`), `content`, and `createdAt`. `MessagePageResponse` contains `items` and nullable `nextBeforeSequence`.

## Turn stream

`POST /api/v1/companion/conversations/{conversationId}/turns` consumes JSON and produces `text/event-stream`:

```json
{"turnId":"<UUID>","content":"Hello","retry":false}
```

`turnId` is required. `content` must be non-blank. To retry a failed turn, send the same `turnId`, identical `content`, and `retry: true`; this does not insert another user message.
Only the latest unanswered user turn may be retried. Once a later user turn is admitted, an earlier failed turn can still be read in history but is no longer retryable.

SSE events are:

| Event | Data |
| --- | --- |
| `delta` | `{"text":"..."}` |
| `status` | `{"code":"ASSESSMENT_LOOKUP"}` |
| `completed` | `{"turnId":"...","assistantMessage":{...},"messageCount":N,"maxMessages":N,"warning":false}` |
| `error` | `{"turnId":"...","code":"...","detail":"...","retryable":false}` |

`status` is a non-terminal progress event emitted immediately before the validated `getLatestAssessmentResult` tool invokes Assessment. It is not emitted for invalid or unavailable calls, budget-skipped calls, or other tool names. Its payload contains only the fixed code; it carries no arguments or assessment data.

Successful streams emit deltas followed by exactly one `completed` event. A stream failure or deadline emits one safe `error` event. Admission, validation and ownership failures before the stream starts use RFC 7807 `ProblemDetail` with `code`, safe detail, and `retryable` where applicable. Statuses are 400 for invalid input, 404 for missing/foreign conversations, 409 for capacity or turn conflicts, 503 for unavailable storage/provider/stream capacity, and 504 for deadline expiry. Authentication and authorization failures remain 401/403.

The conversation cap reserves space for both user and assistant messages before inference begins. `warning` is true once the resulting visible count reaches `ceil(maxMessages * warningPercent / 100)`. A pre-stream quota exhaustion uses status 429 and code `COMPANION_QUOTA_EXHAUSTED`; missing, invalid, or unavailable quota policy uses status 503 and code `COMPANION_QUOTA_UNAVAILABLE`.

Quota availability is checked before inference and before the SSE stream starts. Exhausted policy or unavailable/missing quota policy is rejected through the existing safe `ProblemDetail` path. This adds no request fields or response fields. After a stream begins, missing or invalid authoritative token accounting fails the turn through the existing `error` event; the user message remains available for explicit retry, and no assistant completion is persisted or charged. Quota and provider details are not included in the event.

`completed` is emitted only after the final assistant message and its usage record commit atomically. The additive `status` event does not change request fields, terminal event shapes, persistence or quota behavior.
