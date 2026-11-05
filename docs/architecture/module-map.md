# Module map

| Package | Owns | Boundary |
| --- | --- | --- |
| `user` | User accounts, authentication and profile capabilities | Public `user.contract`; [auth reference](../runbooks/user-auth.md) |
| `dictionary` | Vocabulary and senses | Public lookup contract used by Topic |
| `topic` | Topic authoring, learner progress and quiz/practice | Uses Dictionary and User contracts; [attempts/quiz](../technical/topic/quiz-api-handoff.md), [completion](../technical/topic/completion-api-handoff.md), [retry/reset](../technical/topic/retry-api-handoff.md) |
| `learningpath` | Learning-path selection, day practice and lifecycle | [List API](../technical/learningpath/learning-path-list-api.md), [day practice](../technical/learningpath/learning-path-day-practice-api.md), [lifecycle job](../technical/learningpath/learning-path-lifecycle-job.md); legacy dependency gaps remain subject to architecture policy |
| `subscription` | Plan/policy versions, immutable period benefits, Trial, purchase eligibility/history and product refund eligibility; entitlement source of truth | Imports Payment commands/results; publishes entitlement/policy facts; [technical entry](../technical/subscription/README.md) |
| `payment` | Checkout, financial/payment/refund state, provider verification and reconciliation | Owns command/result contracts; no Subscription import; [boundary reference](../technical/subscription/module-contract.md#payment-owned-contracts) |
| `quota` | Availability, atomic consumption, actual usage ledger/counters, quota windows and selective policy reset | Uses Subscription entitlement contract; exposes `quota.contract`; [boundary reference](../technical/subscription/module-contract.md#quota-owned-capabilities) |
| `assessment` | Learner periodic assessment F01/F02, paper snapshots, timing, grading, immutable review | Uses Subscription/Quota public contracts; [technical reference](../technical/assessment.md) |
| `shared` | Base document types, web/security primitives and domain-neutral durable messaging | No feature business policy |
| `config` | Application wiring and infrastructure configuration | Follow [package policy](package-structure.md) |

Speaking remains a future Quota caller. Assessment F01/F02 implements transactional attempt consumption and reads Subscription analysis access through public contracts. Assessment owns exam timing/media and snapshot results; it does not write User or Learning Path state. Topic/resource access is not a generic Subscription or Quota permission.

The table summarizes responsibility, not a new dependency approval. The authoritative [dependency table](cross-feature-communication.md#approved-feature-dependencies) and [package policy](package-structure.md) govern changes. Do not read another module's collections or use its internal models/repositories. Detailed Payment and Quota documentation remains scoped to their integration with this Subscription pilot; no whole-module migration is implied.

Assessment: [rules and API](../technical/assessment.md). Placement baseline and exact refund-to-analysis delivery remain integration boundaries.
