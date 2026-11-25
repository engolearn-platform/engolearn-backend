# Technical documentation

Start with the [Subscription technical entry](subscription/README.md), then its [HTTP API](subscription/api-contract.md), [data model](subscription/data-model.md) and [module contracts](subscription/module-contract.md). Read the [architecture policies](../README.md) before changing shared boundaries.

The active [legacy OpenAPI file](subscription-quota/quota-api.openapi.yaml) is retained at its original path for frontend compatibility. It covers plan/self/Trial/quota reads and Admin configuration, not the complete purchase/refund/provider API. Use the current HTTP API page for that missing surface.

Topic contracts cover [management reads](topic/management-read-api.md), [authoring](topic/management-authoring-api.md), [attempts/quiz](topic/quiz-api-handoff.md), [completion/results](topic/completion-api-handoff.md) and [retry/reset](topic/retry-api-handoff.md). Earlier implementation logs and sample-data plans are available in Git history under `docs/topic/`.

Learning Path documentation covers [list/recommendation/creation](learningpath/learning-path-list-api.md), [day practice](learningpath/learning-path-day-practice-api.md) and the [lifecycle job](learningpath/learning-path-lifecycle-job.md).

Learning Path management: [passage API contract and implementation sequence](learningpath/learning-path-management-api-plan.md). Creating DRAFT passages with Listening audio is implemented; list/detail/edit/delete are planned.

The [old folder router](subscription-quota/README.md) preserves compatibility. Architectural decisions remain in ADRs; earlier implementation documents and verification records are available in Git history under `docs/technical/subscription-quota/`. Other modules remain under the existing paths linked from the [documentation index](../README.md).

Learner periodic assessment: [F01/F02 rules, API and compatibility](assessment.md).

Speaking AI: [as-built backend entry, REST/WebSocket contract, persistence and runtime](speaking-ai/README.md). The related feature handoff is [Speaking with AI](../features/speaking-with-ai/README.md).

Placement Test: [module entry](placement/README.md), [runtime](placement/placement-local-runtime.md), [question bank](placement/placement-admin-question-bank.md), [test sets](placement/placement-admin-test-sets.md) and [read-only attempt lookup](placement/placement-admin-attempt-api.md).

Grammar Learning: [module entry](grammar/README.md), [V2 storage and rules](grammar/grammar-v2-runtime.md), [import/migration](grammar/grammar-v2-importer.md) and [curriculum](grammar/grammar-cefr-roadmap.md). Historical implementation reports are retained in Git history instead of current module contracts.
