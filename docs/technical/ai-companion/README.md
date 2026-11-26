# AI Companion

AI Companion provides protected, text-only learner conversations backed by MongoDB and Gemini. During a turn, the assistant can request the learner's latest Assessment result on demand and use the learner's long-term memory; completed turns are accounted through Quota. MongoDB is canonical for memory. Qdrant is a derived retrieval index and is never the source of truth.

- [HTTP and SSE contract](api-contract.md)
- [Screen Awareness (M4)](screen-awareness.md)
- [Request lifecycle](runtime-flows.md)
- [Memory proposal validation decision](memory-proposal-validation.md)
- [MongoDB model](data-model.md)
- [Vector recovery runbook](recovery.md)

The feature lives under `fit.iuh.engolearn.companion`. `CurrentUserProvider` supplies the learner identity; service and repository operations scope reads and writes to that identity. `/api/v1/companion/**` requires `ROLE_USER`, which includes the inherited `CONTENT_MANAGER` and `ADMIN` authorities.

Ordinary REST endpoints return `ApiResponse<T>`. The turn endpoint uses a separate Spring MVC `@Controller` with `SseEmitter` because its public wire protocol is `text/event-stream`; this endpoint exception does not change the `@RestController` envelope rule.

An explicitly attached Topic screen context is resolved through the Topic-owned read-only contract before inference. No attachment means no screen lookup. See [Screen Awareness](screen-awareness.md) for the scoped payload, registry, and validation rules.

Assessment data is not preloaded into the initial prompt. When useful, the assistant can request the learner's latest eligible result through the registered read-only `getLatestAssessmentResult` tool. The tool returns a summary by default; the assistant can request visible skill details under the learner's PREVIEW or FULL policy. It does not settle an attempt or create an analysis grant. See [request lifecycle](runtime-flows.md) for tool arguments and limits.

Automatic Memory is effective **ON** when the account has no stored preference. There is no first-use consent step. Learners can turn automatic create/update OFF regardless of a memory's provenance; retrieval and user-requested remember/update/forget remain available while OFF. For chat create/update, the model classifies the trigger; the [validation decision](memory-proposal-validation.md) documents the resulting consent-classification limitation. Memory CRUD/settings and post-commit turn feedback are described in the [HTTP and SSE contract](api-contract.md). Forget is canonical as soon as its Mongo transaction commits, including when Qdrant is unavailable; its recovery receipt contains no forgotten content.

Before inference, Quota checks the learner's effective policy for `AI_COMPANION_TOKEN_USAGE`. The daily token rule and allowance come from the published entitlement policy; this feature defines no allowance amount. A completed turn records provider-reported total-token usage only after the assistant message and quota record commit together. Only fully generated assistant replies are persisted. A failed or cancelled turn keeps its user message for explicit retry; emitted partial assistant text is not saved.
