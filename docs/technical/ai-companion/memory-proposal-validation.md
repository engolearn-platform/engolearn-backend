# Memory proposal validation

**Decision:** Accepted (2026-11-26). Implemented for create/update; targeted automated tests and the initial live Gemini smoke verification passed (2026-11-26). Chat-based Forget retains its existing guard until its authorization design is resolved.

## Context

Gemini extracts or infers useful memories from natural-language conversation. Before this change, the backend checked that an evidence excerpt occurs in the current input, required the proposed content to occur inside that excerpt after normalization, and detected direct memory requests using fixed phrases and negation heuristics. These lexical rules reject valid paraphrases and can accept misleading excerpts that omit qualifiers or negation. The discarded fallback to storing the raw excerpt for direct requests does not solve this semantic mismatch.

## Decision: model-driven semantics, deterministic backend constraints

Gemini is responsible for intent recognition, extraction, conservative inference, classification, and selection of evidence from the current learner message. The backend is responsible for provenance checks, schema validation, authenticated ownership, memory settings, turn/version fences, idempotency and transactional persistence. It does not attempt to independently interpret natural language using substring checks or keyword lists.

For model-proposed create/update, keep the exact excerpt requirement. Validate a nonblank, bounded `evidence` against the server-owned current learner input (for example, `currentInput.contains(evidence.trim())`). Do not accept evidence sourced solely from assistant text, retrieved memories or provider-supplied copies of the user message. Validate the proposed `content` independently for schema and length. Persist the proposed content, not the raw excerpt, after policy and data checks pass.

For create/update, remove semantic substring matching (`supportedByExcerpt`), its negation heuristics, fixed-phrase intent classification, and the fallback that substitutes raw evidence for proposed content. The excerpt check proves provenance of the quote, not semantic entailment, relevance, user intent, or truth.

## Internal tool contract

For create/update, introduce a required model-classified `trigger`: `USER_REQUESTED` or `AUTOMATIC`. This is separate from `source` (`EXPLICIT` or `INFERRED`), which describes the provenance of the information rather than why it is being saved. A directly stated fact can have `source=EXPLICIT` and `trigger=AUTOMATIC`. Update continues to preserve existing category and source. Unknown or missing trigger is invalid. This change is to the internal tool contract, not the public REST/SSE contract. Trigger is included in the operation fingerprint so a retry cannot silently switch between automatic and user-requested writes. Existing create/update receipts produced with the earlier fingerprint format may safely conflict rather than replay.

Example (automatic memory enabled):

```json
{
  "category": "PREFERENCE",
  "content": "The learner prefers studying English in the evening.",
  "source": "EXPLICIT",
  "trigger": "AUTOMATIC",
  "retrievalMode": "RELEVANT",
  "evidence": "I prefer studying English at night"
}
```

The server verifies that the quoted evidence appears in the actual current learner message. It does not require the English paraphrase in content to occur in the evidence. Automatic create/update must still pass the existing settings fences at admission and commit. Retrieval is unaffected by the automatic-memory setting.

## Limitations and privacy boundary

The model can attach a real but irrelevant or selectively quoted excerpt to an incorrect memory. It can also misclassify an automatic proposal as USER_REQUESTED. Thus a model-supplied trigger is a semantic classification, not independently verified user consent. In this MVP, accepting model-classified direct create/update while automatic memory is OFF accepts that residual policy-bypass risk; it must not be described as a hard guarantee of no unsolicited writes. If strict consent is required, require a trusted user action (for example, an authenticated confirmation bound to the specific proposal), rather than another keyword list. An LLM verifier could improve semantic quality but adds cost and latency and is not a deterministic authorization control.

Forget must remain user-directed, never automatic. Removing lexical intent detection also affects chat-based forget: its intent classification has the same limitation. The existing authenticated REST delete remains an independently user-initiated path. This implementation preserves the legacy lexical guard for chat-based Forget only; it can reject natural paraphrases and is not proof of consent. A replacement authorization mechanism remains a separate decision; automatic deletion is not enabled.

Keep MongoDB canonical and Qdrant derived. Keep existing ownership, expected-version, forget-revision, transaction, receipt, retry and post-commit event guarantees. Do not persist or log raw evidence/tool arguments as a side effect of this change; the current data model retains source_turn_id and content-free receipts, so source-turn traceability is limited by the availability of the original conversation. No new evidence storage is required for this decision.

## Verification and maintenance

- Backend tests: accept grounded paraphrases with an exact source excerpt; reject missing, fabricated or out-of-scope evidence and invalid arguments; enforce automatic OFF at admission and commit, including explicitly stated facts saved automatically; preserve owner/version/forget fences, idempotency and post-commit events.
- Model behavior evaluations: cover natural Vietnamese and English direct requests, negation, quotations, hypotheticals, temporary facts, irrelevant evidence, conservative inference and automatic vs user-requested classification. These evaluate semantic quality, not deterministic guarantees.
- End-to-end smoke test: run actual Gemini tool calls on an isolated demo database; confirm canonical persistence and the memory_operation event. Include the automatic OFF path and document residual misclassification risk. Log only safe tool names, decision stages and result codes.

### Verification record (2026-11-26)

- Targeted automated tests: 29 unit tests and 9 MongoDB Testcontainers integration tests passed, with no failures or skips; the full repository suite was not rerun.
- Live smoke: registered a new synthetic learner through the public auth API on an isolated local demo backend. With Automatic Memory OFF, a natural-language direct remember request produced a non-verbatim paraphrase, createMemory returned SUCCESS, a CREATED event was emitted, and the canonical API returned the saved content. A non-request statement triggered createMemory but the backend rejected it with AUTOMATIC_MEMORY_DISABLED and no mutation occurred. A direct update in a new conversation used searchMemory and updateMemory, emitted UPDATED, and returned the same canonical ID with incremented version. All three cases passed. Synthetic memories and conversations were removed through the API; the test account remains in the isolated database.
- Scope: this initial smoke was evidence for those English-language cases, not a guarantee of semantic correctness or trigger classification. The subsequent milestone checks below expanded coverage.
- A second inference verifier or trusted confirmation is a possible later enhancement, not part of this MVP decision.

### M3 verification follow-up (2026-11-26)

- Backend: the full Companion test package plus ArchitectureTest passed 73/73 (zero failures, errors or skips) after adding old-turn/forgotten replay fencing, foreign-owner mutations, failure/stale replay event suppression, vector cleanup failure/reconciliation and first-delete success assertions. The entire repository Maven verify was not rerun; the earlier full-suite failure baseline remains unresolved.
- Live model evaluation: run the opt-in [memory evaluation](../../../tools/ai-companion-evaluation/README.md) against the isolated demo. Initial run: 9/11 scenarios passed, with two review cases. An ordinary durable statement was saved while automatic memory was OFF, demonstrating actual trigger misclassification risk. The cross-chat recall/update/forget scenario recalled and updated correctly, but the fresh canonical delete returned effectCurrent=false, causing NO_DATA and no DELETED event despite the committed deletion. Fixed the fresh-delete result and clarified the OFF prompt. Both failed scenarios passed one targeted recheck; the original nine results were not rerun as a full suite after the prompt change. These observations do not guarantee future model intent classification. Do not describe automatic OFF as independently verified consent.
- Browser integration: actual Chrome on the development machine, Vite port 3001 proxied to isolated backend port 8082, with newly registered synthetic learner. Verified registration, OFF persistence, live Gemini create + inline post-commit SSE, Memory shortcut and draft preservation, edit + version increment, current Qdrant revision, canceled and confirmed Forget, eventual zero Qdrant points for that learner, and ON persistence. Added direct Home navigation from compact Memory/Settings. The first browser automation attempts hit a CDP serialization/navigation issue and the missing direct back action; the final isolated Chrome session passed the functional checks, with Qdrant deletion confirmed separately after the configured cleanup interval rather than the initial 15-second polling bound.
- FE build and lint passed after the navigation fix (0 errors, 74 pre-existing warnings). Synthetic memories and conversations were removed; synthetic accounts remain in the disposable demo DB. No shared development database was dropped. No full production rollout or browser retry/notification-deduplication scenario was verified in this follow-up.

See [request lifecycle](runtime-flows.md), [HTTP and SSE contract](api-contract.md), and [MongoDB model](data-model.md). Update implementation-facing documentation and tests when the target design is implemented.
