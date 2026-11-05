# Module map

| Package | Owns | Boundary |
| --- | --- | --- |
| `user` | User accounts, authentication and profile capabilities | Public `user.contract`; [auth reference](../features/user-auth.md) |
| `dictionary` | Vocabulary and senses | Public lookup contract used by Topic |
| `topic` | Topic authoring, learner progress and quiz/practice | Uses Dictionary and User contracts; [existing integration guide](../topic/live-integration.md) |
| `learningpath` | Learning-path selection, day practice and lifecycle | [List API](../learning-path-list-api.md), [day practice](../learning-path-day-practice-api.md), [lifecycle job](../learning-path-lifecycle-job.md); legacy dependency gaps remain subject to architecture policy |
| `subscription` | Plan/policy versions, immutable period benefits, Trial, purchase eligibility/history and product refund eligibility; entitlement source of truth | Imports Payment commands/results; publishes entitlement/policy facts; [technical entry](../technical/subscription/README.md) |
| `payment` | Checkout, financial/payment/refund state, provider verification and reconciliation | Owns command/result contracts; no Subscription import; [boundary reference](../technical/subscription/module-contract.md#payment-owned-contracts) |
| `quota` | Availability, atomic consumption, actual usage ledger/counters, quota windows and selective policy reset | Uses Subscription entitlement contract; exposes `quota.contract`; [boundary reference](../technical/subscription/module-contract.md#quota-owned-capabilities) |
| `shared` | Base document types, web/security primitives and domain-neutral durable messaging | No feature business policy |
| `config` | Application wiring and infrastructure configuration | Follow [package policy](package-structure.md) |

Speaking and Proficiency are future callers of Quota in this checkout, not implemented producer integrations. Measuring audio and deciding when an assessment starts belong to those product features. Topic/resource access is not a generic Subscription or Quota permission.

The table summarizes responsibility, not a new dependency approval. The authoritative [dependency table](cross-feature-communication.md#approved-feature-dependencies) and [package policy](package-structure.md) govern changes. Do not read another module's collections or use its internal models/repositories. Detailed Payment and Quota documentation remains scoped to their integration with this Subscription pilot; no whole-module migration is implied.
