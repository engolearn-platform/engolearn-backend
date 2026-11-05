# Technical documentation

Start with the [Subscription technical entry](subscription/README.md), then its [HTTP API](subscription/api-contract.md), [data model](subscription/data-model.md) and [module contracts](subscription/module-contract.md). Read the [architecture policies](../README.md) before changing shared boundaries.

The active [legacy OpenAPI file](subscription-quota/quota-api.openapi.yaml) is retained at its original path for frontend compatibility. It covers plan/self/Trial/quota reads and Admin configuration, not the complete purchase/refund/provider API. Use the current HTTP API page for that missing surface.

Topic contracts cover [attempts/quiz](topic/quiz-api-handoff.md), [completion/results](topic/completion-api-handoff.md) and [retry/reset](topic/retry-api-handoff.md). Earlier implementation logs and sample-data plans are available in Git history under `docs/topic/`.

The [old folder router](subscription-quota/README.md) preserves compatibility. Architectural decisions remain in ADRs; earlier implementation documents and verification records are available in Git history under `docs/technical/subscription-quota/`. Other modules remain under the existing paths linked from the [documentation index](../README.md).
