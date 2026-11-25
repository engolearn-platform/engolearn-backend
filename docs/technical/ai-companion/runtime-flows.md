# AI Companion request lifecycle

## Create and browse

The controller delegates to the feature service. The service reads the authenticated learner through `CurrentUserProvider`, and repository queries include that learner ID. Conversation pages use an encoded `(updatedAt,id)` cursor. Message history is loaded newest-first and reversed within the page so each page is chronological; a `beforeSequence` cursor selects older rows.

## Turn admission and streaming

1. The turn controller validates the request and synchronously calls `prepareTurn`. The service checks ownership and either atomically reserves capacity for a new turn and stores its user message, or claims the same pending user message for an explicit retry.
2. A bounded executor with no queue starts provider work. If capacity is unavailable, the controller releases the prepared reservation and returns a safe 503 `ProblemDetail` before returning an emitter.
3. Gemini streams deltas through the observer. A failed inference may be retried once only before a delta has reached the client. After any delta, the stream ends with an error and can be retried explicitly using the same turn ID and content.
4. The service persists the final assistant message only after generation completes and the turn generation still owns the reservation. It returns the persisted message and count for the `completed` event.
5. Provider failure, timeout, client disconnect, send failure, or task rejection releases the local reservation where possible. A Mongo lease and generation fence prevent stale workers from committing after their reservation expires. Cancellation is best effort at the provider; partial assistant output is never persisted.

## Context and provider

The prompt is composed from the versioned static core persona, the learner's current CEFR level, recent prior conversation messages, and the current input once. The current input is excluded from history selection. History defaults to the newest 20 prior messages with a 4,000-token budget and is sent in chronological order.

The Gemini adapter uses the configured model and temperature, caps output tokens, records only safe usage metadata, and maps provider failures to safe feature errors. It does not log prompts, message content, credentials, or raw provider errors.

Runtime settings use the `engolearn.ai-companion` prefix and environment overrides such as `AI_COMPANION_GEMINI_API_KEY`. Defaults are 100 visible messages, a 90% warning threshold, 20 history messages, a 4,000-token history budget, a 30-second inference timeout, a 90-second turn deadline, and up to 16 concurrent streams. The stream executor has no waiting queue; requests that exceed capacity receive a safe 503 response. Set a real API key only in the runtime environment; `.env.example` documents the variable without containing a credential.

## Deletion

Conversation deletion is owner-scoped and transactionally removes its messages with the parent conversation. Long-term memory is not part of this feature.
