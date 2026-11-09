# Speaking AI REST and WebSocket contract

The REST controller is [`SpeakingController`](../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingController.java) under `/api/v1/speaking`. It resolves the authenticated learner from the cookie session. Success is HTTP 200 with `{data, timestamp}`; `data` is nullable for the current-session read. The FE sends `X-Speaking-Contract: 0.8.3` and a matching socket query parameter; this BE does not negotiate or enforce a version from either value.

## REST routes

| Method and path | Request | Behavior |
| --- | --- | --- |
| `GET /items/{itemId}/config` | Path `itemId` | Returns the published Speaking config for an openable Topic item. |
| `GET /sessions/current` | None | Returns the learner's current open session, if present. |
| `GET /sessions?itemId={itemId}` | Optional query | Lists non-deleted learner sessions, optionally for an item. |
| `GET /sessions/{id}` | Path `id` | Returns a learner-owned session. |
| `POST /sessions` | `CreateSpeakingSessionRequest`: `itemId`, `requestId` | Creates a session unless an open session already prevents it. |
| `POST /sessions/replace` | `CreateSpeakingSessionRequest` | Creates with replacement semantics for the learner's existing open session. |
| `POST /sessions/{id}/resume` | `SpeakingControlRequest`: `controllerEpoch`, `requestId` | Ownership is checked, supplied epoch is not used, and resume advances the epoch. Replaying the last resume request ID returns the snapshot without advancing again. |
| `POST /sessions/{id}/end` | `SpeakingControlRequest`: `controllerEpoch`, `requestId` | Ends the session under the current controller epoch. |
| `POST /sessions/{id}/opening/retry` | `SpeakingControlRequest`: `controllerEpoch`, `requestId` | Retries a failed opening under the current controller epoch. |
| `GET /sessions/{id}/opening/audio` | Path `id` | Returns the opening audio playback response when available. |
| `POST /sessions/{id}/socket-ticket` | `SpeakingControlRequest`: `controllerEpoch`, `requestId` | Issues `{ticket, expiresInSeconds: 30}` bound to learner, session and epoch. The request ID is required by the DTO but is not used as a ticket replay key. |
| `DELETE /sessions/{id}` | Path `id` | Soft-deletes the learner-owned session; an open session is settled first. |
| `DELETE /sessions` | None | Soft-deletes the learner's non-deleted sessions; returns the affected count. |
| `GET /sessions/{id}/turns/{turnId}/audio/{kind}` | Turn audio kind `LEARNER_RECORDING` or `AI_REPLY` | Returns `{url, expiresInSeconds: 60}` after ownership and turn membership checks. Opening audio uses its own endpoint and the same URL lifetime. |

The exact Java request and response shapes are [`CreateSpeakingSessionRequest`](../../../src/main/java/fit/iuh/engolearn/speaking/dto/request/CreateSpeakingSessionRequest.java), [`SpeakingControlRequest`](../../../src/main/java/fit/iuh/engolearn/speaking/dto/request/SpeakingControlRequest.java), [`SpeakingSessionResponse`](../../../src/main/java/fit/iuh/engolearn/speaking/dto/response/SpeakingSessionResponse.java), [`SpeakingSocketTicketResponse`](../../../src/main/java/fit/iuh/engolearn/speaking/dto/response/SpeakingSocketTicketResponse.java), and the other DTOs in those folders. Their names and serialized identifiers are compatibility surface; consult the source for exact field constraints.

## Socket handshake and messages

The backend registers `/ws/speaking/*` with configured allowed origins. Request a ticket from `POST /sessions/{id}/socket-ticket`, then connect to `/ws/speaking/{sessionId}?ticket={ticket}`. The ticket is single-use and binds the learner, session and epoch. A missing ticket or failed asynchronous connection setup closes the socket with policy violation. Replacing a controller fences its old connection.

Text messages are JSON. Supported commands are:

```json
{"type":"START_TURN","turnId":"…","controllerEpoch":1}
{"type":"END_OF_TURN","turnId":"…","controllerEpoch":1}
{"type":"CANCEL_TURN","turnId":"…","controllerEpoch":1}
```

Binary frames carry mono 16 kHz signed 16-bit little-endian PCM. The backend bounds recording to 120 seconds and 3,840,000 bytes; input past the bound triggers `TURN_FAILED`. Commands use a turn ID matching `[A-Za-z0-9_-]{1,64}` and the current controller epoch.

| Server event | Meaning |
| --- | --- |
| `CONNECTED`, `OPENING_UPDATED`, `SESSION_ENDED` | Server snapshots for connection, opening changes, or terminal state. |
| `STT_READY`, `INTERIM_TRANSCRIPT`, `TURN_ACCEPTED` | Provider ready, provisional text, and committed transcript respectively. |
| `TURN_CANCELED`, `TURN_EMPTY`, `TURN_FAILED`, `STT_ERROR` | Canceled recording, no finalized text to commit, failed turn, or recognition error. |
| `TURN_RESULT`, `PROCESSING_ERROR` | Settled result or evaluation error with snapshot on the evaluation path. Manager decisions can add optional `demoDecision`; clients must not depend on it for lifecycle state. |
| `AI_AUDIO_READY`, `TTS_ERROR`, `AUDIO_ERROR` | Audio becomes available after the result, synthesis fails, or recording persistence fails. Ready events carry no snapshot; refresh session detail for availability. |
| `CONTROLLER_CHANGED`, `ERROR` | Control has moved, or the handler rejected a message. |

Realtime events carry `controllerEpoch` and `revision`; `turnId` is present for turn events. Handler-level `ERROR` can contain only `type` and `message`. See [`SpeakingRealtimeService`](../../../src/main/java/fit/iuh/engolearn/speaking/service/SpeakingRealtimeService.java) and the [FE client contract](../../../../speaking-ai-baseline-frontend/docs/features/speaking-with-ai/api-contract.md).

Unknown text message types produce an `ERROR` event. Parse/audio exceptions also produce `ERROR` with the exception message when present. Failure to send an event closes the connection with server error. The browser's Vite proxy is configured on the frontend and is not part of this backend contract.

## Error boundaries

The availability gate returns 404 `SPEAKING_DISABLED` for `/api/v1/speaking/` and `/ws/speaking/` prefixes when closed. When open, protected REST requires authentication (401); [`SpeakingExceptionHandler`](../../../src/main/java/fit/iuh/engolearn/speaking/controller/SpeakingExceptionHandler.java) maps validation to 400, business exceptions to their status and code, and optimistic-lock/unhandled duplicate-key conflicts to 409 `STATE_CHANGED`. Examples: 403 `ITEM_UNAVAILABLE` or `SESSION_FORBIDDEN`, 409 `SESSION_EXISTS` or `CONTROLLER_CHANGED`, 410 `AUDIO_EXPIRED`, 429 `DAILY_QUOTA`, and 503 `CONFIG_MISSING` or `CONFIG_INVALID`. Socket errors after upgrade are events or close statuses.
