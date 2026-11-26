# AI Companion vector recovery runbook

MongoDB is the canonical memory store. Qdrant is a derived index. A vector incident can reduce retrieval quality, but it does not roll back a committed memory mutation or a Forget operation.

## Normal recovery behavior

- A committed create/update marks the current Mongo record `PENDING` and signals the single sequential worker with its memory ID and version.
- The worker serially handles after-commit signals and also drains bounded batches of pending records and cleanup receipts, covering missed in-process signals.
- Each transient operation gets one retry after its initial attempt. After retry exhaustion, the memory or cleanup receipt is marked `FAILED`; failed work is not retried continuously.
- A Forget transaction deletes the canonical record and writes a content-free cleanup receipt before returning success. A failed Qdrant delete leaves that receipt available for repair. Retrieval revalidates candidate IDs against Mongo and drops missing or superseded records.

## Bounded startup reconciliation

Use the one-shot startup reconciliation only when an operator has diagnosed a stale or incomplete dedicated index and can restart the service in a controlled window. It is off by default.

1. Confirm MongoDB transactions are available and the configured Qdrant endpoint and collection are reachable. The default collection is `companion_memories`; the collection is configurable.
2. Start the backend once with `--engolearn.ai-companion.memory.reconcile-on-start=true`. This enables a bounded reconciliation for that startup; it is not a recurring mode.
3. The reconciler pages through current canonical memories and reindexes them, retries outstanding cleanup receipts, and scans the dedicated collection to delete orphan points. It uses the same sequential sync worker and configured page limit.
4. Check backend logs/health and Mongo state for completion: current records should reach `vector_sync_state=SYNCED`; successful cleanup receipts should be removed. Failed work remains marked `FAILED` for a later operator-directed reconciliation.
5. Restart normally with reconciliation disabled. Confirm memory list/edit/forget endpoints remain usable and a retrieval query behaves as expected.

Do not clear cleanup receipts or drop/recreate the collection as a workaround. Cleanup receipts contain no forgotten text or embedding, and provide the durable identifier needed to delete the stale point. Reconciliation is idempotent: stable point IDs and current versions make repeated bounded passes safe.

## Settings

All settings use prefix `engolearn.ai-companion.memory`:

| Property | Purpose |
| --- | --- |
| `enabled` | Enables memory functionality. |
| `embedding-model` | Embedding model name; default is `gemini-embedding-2`. |
| `dimension` | Vector size; expected value is `768`. |
| `qdrant-url` | Qdrant service endpoint. |
| `collection` | Dedicated collection name; default is `companion_memories`. |
| `top-k` | Maximum vector candidates retrieved. |
| `similarity-threshold` | Candidate similarity cutoff. |
| `request-timeout` | Embedding/vector request timeout. |
| `sync-batch-size` | Maximum rows drained per sync batch. |
| `reconcile-on-start` | Enables the one-shot bounded startup reconciliation; default `false`. |
| `reconcile-page-limit` | Bounds reconciliation work per page/pass. |

The configured embedding model has been checked with synthetic input: it returned HTTP 200 and 768 finite dimensions. This is a compatibility check, not an operational latency or retrieval-quality benchmark.
