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
| `memory_operation` | `{"operationId":"<UUID>","action":"CREATED","memoryId":"<UUID>","version":1}` |
| `completed` | `{"turnId":"...","assistantMessage":{...},"messageCount":N,"maxMessages":N,"warning":false}` |
| `error` | `{"turnId":"...","code":"...","detail":"...","retryable":false}` |

`memory_operation` is additive and does not change the request or the existing terminal event payloads. It is sent only after the canonical Mongo mutation and its operation receipt commit, before that tool result is returned to the model. `action` is `CREATED`, `UPDATED`, or `DELETED`. Search/read operations, no-ops, validation failures, and pre-commit failures emit no success event. A replay of a still-current effect re-emits the same `operationId`; if the effect has since been superseded or forgotten, the replay emits no stale-content success event. Delivery is best effort: there is no SSE reconnect/result-replay endpoint.

`status` is a non-terminal progress event emitted immediately before the validated `getLatestAssessmentResult` tool invokes Assessment. It is not emitted for invalid or unavailable calls, budget-skipped calls, or other tool names. Its payload contains only the fixed code; it carries no arguments or assessment data.

Successful streams emit deltas followed by exactly one `completed` event. A stream failure or deadline emits one safe `error` event. Admission, validation and ownership failures before the stream starts use RFC 7807 `ProblemDetail` with `code`, safe detail, and `retryable` where applicable. Statuses are 400 for invalid input, 404 for missing/foreign conversations, 409 for capacity or turn conflicts, 503 for unavailable storage/provider/stream capacity, and 504 for deadline expiry. Authentication and authorization failures remain 401/403.

The conversation cap reserves space for both user and assistant messages before inference begins. `warning` is true once the resulting visible count reaches `ceil(maxMessages * warningPercent / 100)`. A pre-stream quota exhaustion uses status 429 and code `COMPANION_QUOTA_EXHAUSTED`; missing, invalid, or unavailable quota policy uses status 503 and code `COMPANION_QUOTA_UNAVAILABLE`.

Quota availability is checked before inference and before the SSE stream starts. Exhausted policy or unavailable/missing quota policy is rejected through the existing safe `ProblemDetail` path. This adds no request fields or response fields. After a stream begins, missing or invalid authoritative token accounting fails the turn through the existing `error` event; the user message remains available for explicit retry, and no assistant completion is persisted or charged. Quota and provider details are not included in the event.

`completed` is emitted only after the final assistant message and its usage record commit atomically. The additive `status` event does not change request fields, terminal event shapes, persistence or quota behavior.

## Long-term memory

Base path: `/api/v1/companion/memory`. All endpoints require `ROLE_USER`, are owner-scoped using the authenticated learner identity, and return `ApiResponse<T>`. Missing and foreign-owned memory IDs have the same `404 MEMORY_NOT_FOUND` response.

| Method and path | Request | Response |
| --- | --- | --- |
| `GET /api/v1/companion/memory?category=<category>&before=<cursor>&limit=20` | Optional category, cursor, and page size (`1`–`100`; default `20`). | `200 ApiResponse<MemoryPageResponse>` with `items` and nullable `nextCursor`. |
| `PATCH /api/v1/companion/memory/{memoryId}` | `{"content":"...","expectedVersion":3}` | `200 ApiResponse<MemoryResponse>` with the updated version. |
| `DELETE /api/v1/companion/memory/{memoryId}?expectedVersion=3` | Expected version is required. | `200 ApiResponse<{id,deleted}>`, with `deleted: true`. |
| `GET /api/v1/companion/memory/settings` | — | `200 ApiResponse<MemorySettingsResponse>`. |
| `PUT /api/v1/companion/memory/settings` | `{"automaticMemoryEnabled":false}` | `200 ApiResponse<MemorySettingsResponse>`. |

Each memory item contains `id`, `category`, `content`, `source` (`EXPLICIT` or `INFERRED`), `version`, `createdAt`, and `updatedAt`. `MemorySettingsResponse` contains the effective `automaticMemoryEnabled` boolean and `updatedAt`. An absent or null stored setting reads as `true`; reading settings does not prompt for consent. Turning the setting off blocks create/update classified as automatic regardless of memory provenance; retrieval and user-directed remember/update/forget remain available. Chat create/update trigger classification is model-driven and not independent proof of consent; see [Memory proposal validation](memory-proposal-validation.md). A directly shared fact may be saved automatically with `source=EXPLICIT` when automatic memory is on.

An update or delete with a stale `expectedVersion` returns `409 MEMORY_VERSION_CONFLICT`; invalid input returns `400`, and unavailable persistence returns `503`. Forget deletes the Mongo record and creates its content-free vector cleanup receipt in one transaction. The response does not wait for Qdrant; after commit the deleted record is immediately excluded from retrieval. See the [vector recovery runbook](recovery.md) for Qdrant recovery and manual reconciliation.
