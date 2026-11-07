# Subscription technical entry

Subscription owns published plans/quota policies, immutable benefits for periods, one-time Trial, purchase intent/eligibility/history, product refund eligibility and effective entitlements. Payment owns provider checkout, financial transactions, verification and reconciliation. Quota owns enforcement, calendar allowances and usage. Subscription does not read their collections or decide Topic/resource access.

Start with [flows](../../flows/subscription/README.md), then [HTTP API](api-contract.md), [data model](data-model.md) or [module contracts](module-contract.md). The [legacy OpenAPI](../subscription-quota/quota-api.openapi.yaml) remains an active frontend contract with limited coverage; it omits purchase/refund/provider routes.

| Package under `fit.iuh.engolearn.subscription` | Main responsibilities |
| --- | --- |
| `controller`, `dto.request`, `dto.response` | Learner/Admin HTTP adaptation, typed validation and projections |
| `service`, `service.impl` | `SubscriptionServiceImpl` resolves entitlements and Trial; `PurchaseServiceImpl` creates/reads intent; result/paid-activation/refund capabilities apply guarded transitions |
| `service.impl` publication/activation capabilities | Immutable configuration publication, global revision and scheduled activation |
| `model`, `model.vo`, `model.enums` | Owned documents, embedded snapshots and stored lifecycle values |
| `repository` | Typed persistence operations and Mongo implementations |
| `contract` | Effective entitlement interface, DTOs and Subscription-owned facts |
| `infra.event`, `infra.scheduling` | Durable Payment-result consumer and activation trigger |

Inspect the [service implementations](../../../src/main/java/fit/iuh/engolearn/subscription/service/impl), [models](../../../src/main/java/fit/iuh/engolearn/subscription/model) and relevant callers before editing. Business implementations have different roles; do not merge request orchestration, provider outcomes and entitlement ownership by following a legacy class mechanically.

Key constraints:

- Purchase idempotency is `(userId, UUID key)` plus a pinned selection fingerprint; replay does not reprice. Account revision and a partial unique blocking-purchase index serialize competing intents. Account provenance is required; missing legacy provenance is unavailable, not automatically a fresh eligible account.
- Producer business state and Outbox commit together. Consumers commit receipt plus effects in an independent local transaction. Recipient leases/fencing and business uniqueness provide at-least-once duplicate safety; transport is not exactly once. See [delivery contract](module-contract.md#delivery-and-transactions).
- Activation validates identity, amount/currency and time before creating one period per payment. Premium calendar duration begins at verified `paidAt`. Trial supersede, account/purchase, receipt and entitlement fact commit together; time determines current rights even when stored labels lag.
- Effective entitlement resolution shares one per-user monotonic sequence across writers. A fingerprint change advances the account version and appends the fact atomically; existing null fingerprints migrate lazily. No account/history reset or pre-Outbox lost-event reconstruction is performed.
- Refund requests pin their product window and stable reference; only verified correlated success revokes. `UNKNOWN` and review holds are not failures. Tier changes/refunds never reset Quota usage.
- Publication writes the GLOBAL configuration revision with policy/plan/receipt. Historical unit/period invariants span scopes. Existing period snapshots are immutable; a new Premium/Trial policy affects new periods. FREE activation selectively resets changed quota types through durable delivery.
- MongoDB replica-set transaction support and required unique indexes are operational prerequisites. No cleanup TTL or manual replay UI is implemented; unresolved records and deduplication history must be retained.

| Area | Implementation | Runtime evidence / limitation |
| --- | --- | --- |
| Plans, self, Trial, Admin publication, Quota engine | Implemented | Earlier scoped verification records are available in Git history; no new application tests in this migration |
| Purchase/history, paid activation, refund core, Outbox/receipts | Implemented | Source verified; no new runtime exercise |
| Optional VNPay PAY/IPN/Return/QueryDR/full-refund worker | Implemented, disabled by default | User reported Sandbox IPN success and Premium activation; not independently retested |
| Historical QueryDR API 99 | Recovery/diagnostics implemented | Historical affected order not proven resolved |
| Provider refund end-to-end | Adapter/core implemented | Unverified with real Sandbox merchant |
| Speaking/Proficiency usage callers | Not integrated | Contracts exist; test callers are not product integration |
| Operator financial review/replay UI, retention cleanup, population-scale activation | Not implemented / not established | Separate operational work; no production-readiness claim |

[VNPay operations](../../runbooks/vnpay.md), [temporary dev release](../../runbooks/dev-purchase-reset.md), and [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md) provide the next level of context. Earlier designs and verification records are available in Git history under `docs/technical/subscription-quota/`. Business rules remain owned by the [business module](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/00-module.md); this pilot documents actual code and flags gaps rather than changing those rules.
