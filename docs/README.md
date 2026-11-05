# Backend documentation

Start with the [system overview](architecture/system-overview.md) and [module map](architecture/module-map.md), then read the documents relevant to your change. Check the implementation before changing a contract or describing its status.

| Need | Source |
| --- | --- |
| Package, dependency and coding rules | [Package structure](architecture/package-structure.md), [communication](architecture/cross-feature-communication.md), [implementation conventions](architecture/implementation-conventions.md) |
| Why an architecture choice exists | [ADR-0001](architecture/decisions/ADR-0001-no-outbox-for-quota-mvp.md), [ADR-0002](architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md) |
| Understand behavior | [Flows](flows/README.md) |
| Change a module or HTTP contract | [Technical documentation](technical/README.md) |
| Configure or troubleshoot VNPay | [VNPay runbook](runbooks/vnpay.md) |
| Temporary development purchase release | [DEV-PAYMENT-RESET-01](runbooks/dev-purchase-reset.md) |
| Task process and completion | [Working agreement](agent/working-agreement.md), [Definition of Done](agent/definition-of-done.md) |

Module references: [authentication](runbooks/user-auth.md), [learning-path list](technical/learningpath/learning-path-list-api.md), [day practice](technical/learningpath/learning-path-day-practice-api.md), [lifecycle job](technical/learningpath/learning-path-lifecycle-job.md), and Topic [attempts/quiz](technical/topic/quiz-api-handoff.md), [completion/results](technical/topic/completion-api-handoff.md) and [retry/reset](technical/topic/retry-api-handoff.md). Topic keeps concise current contracts rather than implementation handoff logs.

Architecture owns shared rules; flows own behavior; technical pages own module implementation/contracts; runbooks own practical operations. Link to the owner instead of duplicating it. Update affected pages when implementation changes. Keep architectural decisions in ADRs and use Git for implementation history and earlier verification records instead of maintaining duplicate snapshots. Earlier Subscription documents are available in Git history under `docs/technical/subscription-quota/`; dated evidence does not establish current runtime status.

Learner assessment: [technical contract](technical/assessment.md).
