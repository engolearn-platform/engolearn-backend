# AI Companion MongoDB model

The conversation layer stores conversation state and immutable visible messages. Long-term memory uses separate owner-scoped canonical records, account settings, operation receipts, and vector-cleanup receipts.

## `companion_conversations`

The conversation document is owned by `learner_id`; all lookups and mutations include that owner. It stores the nullable title, visible `message_count`, next sequence, active turn ID, active generation, lease expiry, and the pending turn ID/user sequence used for explicit retry. MongoDB conditional updates enforce message capacity and one active turn while fencing stale workers by generation.

Conversation list queries are owner-scoped and ordered by `updated_at` descending then ID descending. The encoded cursor contains the last row's timestamp and ID.

## `companion_messages`

Messages store `conversation_id`, `turn_id`, `sequence`, `role`, and `content` alongside inherited ID and audit timestamps. A unique compound index on `(conversation_id, sequence)` preserves per-conversation ordering. A unique `(conversation_id, turn_id, role)` index prevents duplicate user or assistant messages for one turn.

The user message is saved at admission, before inference, so failures remain readable and retryable. Assistant messages are saved only after the complete response, authoritative usage metadata, and active generation have been validated. The assistant message and its Quota usage record commit in the same Mongo transaction before the `completed` event is sent. Quota owns its usage ledger/counters; Companion adds no quota collection. The usage record uses `AI_COMPANION_TOKEN_USAGE`, source feature `AI_COMPANION`, and stable idempotency key `turn:<conversationId>:<turnId>`.

An optional embedded `screen_context` on USER messages stores only the reference (type and topic_id, or attempt_id, stage and item_index). Older messages without it remain valid. A retry reauthorizes and resolves the stored reference; the resolved lesson payload is never persisted as conversation context.

Stream deltas, prompt context, Assessment results, tool arguments/results, and the opaque provider continuation are transient and never stored in Companion documents. The continuation is passed unchanged through the provider-neutral Companion contract and interpreted only inside the Gemini adapter; it is not logged or exposed to the client. If final usage metadata is absent or invalid, or the joint completion transaction fails, the user message remains for explicit retry while no assistant message or usage charge is committed.

## `companion_memories`

MongoDB is canonical. A memory document stores `learner_id`, `category`, `content`, `source` (`EXPLICIT` or `INFERRED`), `retrieval_mode`, `source_turn_id`, `version`, and `vector_sync_state`, plus inherited `_id`, `created_at`, and `updated_at`. The owner/update/ID compound index supports owner-scoped cursor paging. Every read and mutation is scoped to the authenticated learner. Updates use an expected version; a stale version conflicts instead of overwriting a newer edit. A missing or foreign ID is indistinguishable (`MEMORY_NOT_FOUND`).

`companion_memory_settings` has one row per `learner_id`, `automatic_memory_enabled`, `forget_revision`, and `mutation_fence`. `automatic_memory_enabled` may be absent or null; both mean effective ON. No consent record or first-use prompt is required. Turning it OFF gates create/update classified as automatic regardless of provenance; it does not disable retrieval or direct user-requested memory actions. See [Memory proposal validation](memory-proposal-validation.md) for the model-driven classification and its limitations. An automatically saved directly shared fact may have `source=EXPLICIT`. A forget increments the revision so an older admitted/retried turn cannot recreate the forgotten content from conversation history.

`companion_memory_operation_receipts` stores one durable receipt per tool mutation, committed atomically with the canonical mutation. Its deterministic `_id` derives from `conversationId`, `turnId`, and server-assigned `ordinal`, so uniqueness does not depend on index creation. Fields are `conversation_id`, `turn_id`, `ordinal`, `fingerprint`, `action`, `memory_id`, `memory_version`, `result_code`, and `committed_at`. Provider tool-call IDs are correlation data, not idempotency keys. The keyed fingerprint is over normalized arguments, including the create/update trigger. Retrying the same operation reuses its stable `operationId`; the same ordinal with changed arguments conflicts. Receipts contain no memory content, including forgotten text or embeddings. A replay reports success only while the recorded effect is still current.

## `companion_memory_vector_cleanup`

Forget deletes the canonical memory, advances `forget_revision`, and inserts a unique cleanup receipt in one Mongo transaction. Receipt fields are `point_id`, `learner_id`, `deletion_version`, `deleted_at`, and `retry_state` (`PENDING` or `FAILED`). This record intentionally contains no content, embedding, prompt, history, or payload copy. A retrieval candidate is hydrated against current Mongo records; if the canonical record is gone, it is dropped before reaching the model. Thus Forget takes effect at transaction commit even if Qdrant is down, and cleanup can retry without restoring the deleted memory.

## Qdrant derived index

The dedicated collection defaults to `companion_memories` and is configurable. Points use the canonical Mongo UUID as stable point ID, a 768-dimensional cosine vector, and only these payload keys: `learner_id`, `version`, `category`, and `retrieval_mode`. Memory text remains in Mongo and is not copied into Qdrant payload. New or updated canonical records begin with `vector_sync_state=PENDING`; successful indexing marks the matching current version `SYNCED`, and exhausted/failed work is `FAILED`. Conditional version updates and current-record hydration prevent stale vectors from surfacing superseded or deleted content. Vector health/status is internal and is not exposed in REST responses.

## A2.4 first-turn receipt
The companion_first_turn_receipts collection uses a deterministic owner + request UUID _id (Mongo unique primary index). It stores owner, request identity, receipt state ADMITTED/CANCELLED, and canonical conversation reference where admitted. No message text, title, context or payload hash is duplicated into the receipt; payload comparison uses the canonical USER and stored context. Admission writes receipt, conversation (first-message title, count/sequence and active reservation) and USER in one Mongo transaction. Cancel installs a content-free fence atomically; a deleted conversation does not remove the fence or permit resurrection. Retention lifecycle must be decided before production rollout; no cleanup job is added in A2.
