# AI Companion

AI Companion provides protected, text-only learner conversations backed by MongoDB and Gemini. This page links the current backend contract, stream lifecycle, persistence model and runtime settings.

- [HTTP and SSE contract](api-contract.md)
- [Request lifecycle](runtime-flows.md)
- [MongoDB model](data-model.md)

The feature lives under `fit.iuh.engolearn.companion`. `CurrentUserProvider` supplies the learner identity; service and repository operations scope reads and writes to that identity. `/api/v1/companion/**` requires `ROLE_USER`, which includes the inherited `CONTENT_MANAGER` and `ADMIN` authorities.

Ordinary REST endpoints return `ApiResponse<T>`. The turn endpoint uses a separate Spring MVC `@Controller` with `SseEmitter` because its public wire protocol is `text/event-stream`; this endpoint exception does not change the `@RestController` envelope rule.

Only fully generated assistant replies are persisted. A failed or cancelled turn keeps its user message for explicit retry; emitted partial assistant text is not saved.
