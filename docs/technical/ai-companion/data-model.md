# AI Companion MongoDB model

Two collections store conversation state and immutable visible messages.

## `companion_conversations`

The conversation document is owned by `learner_id`; all lookups and mutations include that owner. It stores the nullable title, visible `message_count`, next sequence, active turn ID, active generation, lease expiry, and the pending turn ID/user sequence used for explicit retry. MongoDB conditional updates enforce message capacity and one active turn while fencing stale workers by generation.

Conversation list queries are owner-scoped and ordered by `updated_at` descending then ID descending. The encoded cursor contains the last row's timestamp and ID.

## `companion_messages`

Messages store `conversation_id`, `turn_id`, `sequence`, `role`, and `content` alongside inherited ID and audit timestamps. A unique compound index on `(conversation_id, sequence)` preserves per-conversation ordering. A unique `(conversation_id, turn_id, role)` index prevents duplicate user or assistant messages for one turn.

The user message is saved at admission, before inference, so failures remain readable and retryable. Assistant messages are saved only after the complete response has been generated and its active generation is still valid. Stream deltas are transient and never stored.
