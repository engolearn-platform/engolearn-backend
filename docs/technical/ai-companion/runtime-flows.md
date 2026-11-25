# AI Companion request lifecycle

## Create and browse

The controller delegates to the feature service. The service reads the authenticated learner through `CurrentUserProvider`, and repository queries include that learner ID. Conversation pages use an encoded `(updatedAt,id)` cursor. Message history is loaded newest-first and reversed within the page so each page is chronological; a `beforeSequence` cursor selects older rows.

## Turn admission and streaming

1. The turn controller validates the request and synchronously calls `prepareTurn`. The service checks ownership and either atomically reserves capacity for a new turn and stores its user message, or claims the same pending user message for an explicit retry.
2. Before inference, the service asks Quota to check `AI_COMPANION_TOKEN_USAGE` availability against the learner's effective entitlement. This is a point-in-time check, not a reservation. Exhausted allowance or unavailable/missing policy fails closed before the stream begins. The daily window follows Quota's `Asia/Ho_Chi_Minh` calendar and the published policy; no token allowance is defined in Companion.
3. Initial prompt context combines the versioned persona, current CEFR level, recent history, and current input. Assessment results are not fetched or preloaded into this prompt. When needed, the app-controlled tool loop exposes the typed, read-only `getLatestAssessmentResult` tool, backed by Assessment's owner-scoped `AssessmentCompanionResults` contract. Tool arguments accept `detail` (`SUMMARY` or `SKILLS`, default `SUMMARY`) and optional `skillCodes` (up to five codes, valid with `SKILLS`). A summary returns official score, level, submission time and scope. `SKILLS` returns only rows visible to the learner: at most two for `PREVIEW` and five for `FULL`; supplied codes filter that visible subset, while omitting them returns all visible rows. No eligible result returns `NO_DATA`. The lookup excludes ineligible results and does not settle attempts or persist a newly observed grant.
4. Tool calls are sequential and bounded by configured call and round limits (default maximum five each). Unknown or malformed calls receive a safe tool result. Ordinary answer text is streamed as `delta`; provisional text is suppressed after tool-call frames. A provider failure may be retried once only before any delta reaches the client. After visible output, failure ends through the existing `error` event and the same turn ID/content can be explicitly retried.
5. Each successful model round must provide authoritative cumulative `totalTokenCount` metadata. Companion adds that total once for each successful round using checked arithmetic; it does not add prompt and completion counts on top of the total. Missing or invalid total usage fails safely: no assistant message, quota record, or `completed` event is committed, and the user message remains available for explicit retry.
6. After generation and usage validation, one Mongo transaction persists the final assistant message and calls Quota's `recordUsage` for the aggregate under the stable key `turn:<conversationId>:<turnId>`. `completed` is emitted only after this transaction commits. Quota records actual usage truthfully, including concurrent over-limit usage; availability is not a guarantee that a later record stays within the limit.
7. Provider failure, timeout, client disconnect, send failure, or task rejection releases the local reservation where possible. A Mongo lease and generation fence prevent stale workers from committing after their reservation expires. Cancellation is best effort at the provider; partial assistant output is never persisted.

## Context and provider

The initial prompt is composed from the versioned static core persona, the learner's current CEFR level, recent prior conversation messages, and the current input once. Assessment data is retrieved only on demand through the tool. The current input is excluded from history selection. History defaults to the newest 20 prior messages with a 4,000-token budget and is sent in chronological order. Tool results and provider continuation state are temporary request-time context, not persisted memory.

The Companion service uses EngoLearn-owned request, message, event, tool-call and tool-result types. Its continuation marker is opaque: the service passes it back unchanged, while the Gemini adapter alone interprets and stores the provider-native continuation in memory for the active turn. Continuations are not persisted, logged or exposed in SSE. The Gemini adapter contains the direct Google GenAI SDK integration. Neither Spring AI model classes nor Google GenAI SDK types cross the Companion model contract.

The Gemini adapter uses the configured model and temperature, caps output tokens, records only safe usage metadata, and maps provider failures to safe feature errors. It does not log prompts, message content, credentials, or raw provider errors.

Runtime settings use the `engolearn.ai-companion` prefix and environment overrides such as `AI_COMPANION_GEMINI_API_KEY`. Defaults are 100 visible messages, a 90% warning threshold, 20 history messages, a 4,000-token history budget, a 30-second inference timeout, a 90-second turn deadline, and up to 16 concurrent streams. The stream executor has no waiting queue; requests that exceed capacity receive a safe 503 response. Set a real API key only in the runtime environment; `.env.example` documents the variable without containing a credential.

## Deletion

Conversation deletion is owner-scoped and transactionally removes its messages with the parent conversation. Long-term memory is not part of this feature.
