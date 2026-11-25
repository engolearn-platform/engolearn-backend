# AI Companion

AI Companion provides protected, text-only learner conversations backed by MongoDB and Gemini. During a turn, the assistant can request the learner's latest Assessment result on demand; completed turns are accounted through Quota. This page links the backend contract, stream lifecycle, persistence model and runtime settings.

- [HTTP and SSE contract](api-contract.md)
- [Request lifecycle](runtime-flows.md)
- [MongoDB model](data-model.md)

The feature lives under `fit.iuh.engolearn.companion`. `CurrentUserProvider` supplies the learner identity; service and repository operations scope reads and writes to that identity. `/api/v1/companion/**` requires `ROLE_USER`, which includes the inherited `CONTENT_MANAGER` and `ADMIN` authorities.

Ordinary REST endpoints return `ApiResponse<T>`. The turn endpoint uses a separate Spring MVC `@Controller` with `SseEmitter` because its public wire protocol is `text/event-stream`; this endpoint exception does not change the `@RestController` envelope rule.

Assessment data is not preloaded into the initial prompt. When useful, the assistant can request the learner's latest eligible result through the registered read-only `getLatestAssessmentResult` tool. The tool returns a summary by default; the assistant can request visible skill details under the learner's PREVIEW or FULL policy. It does not settle an attempt or create an analysis grant. See [request lifecycle](runtime-flows.md) for tool arguments and limits.

Before inference, Quota checks the learner's effective policy for `AI_COMPANION_TOKEN_USAGE`. The daily token rule and allowance come from the published entitlement policy; this feature defines no allowance amount. A completed turn records provider-reported total-token usage only after the assistant message and quota record commit together. Only fully generated assistant replies are persisted. A failed or cancelled turn keeps its user message for explicit retry; emitted partial assistant text is not saved.
