# AI Companion MongoDB model

Two collections store conversation state and immutable visible messages.

## `companion_conversations`

The conversation document is owned by `learner_id`; all lookups and mutations include that owner. It stores the nullable title, visible `message_count`, next sequence, active turn ID, active generation, lease expiry, and the pending turn ID/user sequence used for explicit retry. MongoDB conditional updates enforce message capacity and one active turn while fencing stale workers by generation.

Conversation list queries are owner-scoped and ordered by `updated_at` descending then ID descending. The encoded cursor contains the last row's timestamp and ID.

## `companion_messages`

Messages store `conversation_id`, `turn_id`, `sequence`, `role`, and `content` alongside inherited ID and audit timestamps. A unique compound index on `(conversation_id, sequence)` preserves per-conversation ordering. A unique `(conversation_id, turn_id, role)` index prevents duplicate user or assistant messages for one turn.

The user message is saved at admission, before inference, so failures remain readable and retryable. Assistant messages are saved only after the complete response, authoritative usage metadata, and active generation have been validated. The assistant message and its Quota usage record commit in the same Mongo transaction before the `completed` event is sent. Quota owns its usage ledger/counters; Companion adds no quota collection. The usage record uses `AI_COMPANION_TOKEN_USAGE`, source feature `AI_COMPANION`, and stable idempotency key `turn:<conversationId>:<turnId>`.

Stream deltas, prompt context, Assessment results, tool arguments/results, and the opaque provider continuation are transient and never stored in Companion documents. The continuation is passed unchanged through the provider-neutral Companion contract and interpreted only inside the Gemini adapter; it is not logged or exposed to the client. If final usage metadata is absent or invalid, or the joint completion transaction fails, the user message remains for explicit retry while no assistant message or usage charge is committed.
