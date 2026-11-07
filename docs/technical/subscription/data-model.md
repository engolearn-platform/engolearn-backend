# Subscription data model

MongoDB collection ownership follows the module. References such as `payment_id` are values, not permission to read Payment storage. The [model annotations](../../../src/main/java/fit/iuh/engolearn/subscription/model) define exact stored snake_case fields/index names; repository predicates also enforce transitions. Index declarations are source evidence, not confirmation that a deployed database has created them.

| Subscription collection / document | Business data and declared indexes |
| --- | --- |
| `subscription_accounts` / `SubscriptionAccount` | User ID as document identity, provenance, lifetime Trial flag, revision, entitlement version/fingerprint; serializes concurrent changes |
| `subscription_periods` / `SubscriptionPeriod` | User, kind `TRIAL/MONTHLY/ANNUAL`, state `ACTIVE/EXPIRED/REFUNDED/SUPERSEDED`, interval, immutable policy/benefits/prices, payment ID and superseded time. Unique partial user Trial, unique partial payment ID; user/start-desc history index |
| `subscription_purchases` / `PremiumPurchase` | Reference/user/UUID intent key/fingerprint, pinned plan/policy versions and snapshots/selected price, amount/currency, deadline, intent time, status, blocking flag, financial/period/refund projection, review and dev-reset audit. Unique reference; unique user/key; unique partial user where blocking; unique partial string payment ID; user/created-desc/ID-desc history |
| `subscription_refund_requests` / `SubscriptionRefundRequest` | Stable reference, purchase/payment/user, actor/reason/key, state and result/times. Unique purchase/key and refund reference; user/status pending lookup |
| `subscription_plan_versions` / `PlanVersion` | Immutable scope/version/effective time and plan card; unique scope/version |
| `subscription_quota_policy_versions` / `PolicyVersion` | Immutable scope/version/effective time, quota snapshot and benefits; unique scope/version |
| `subscription_policy_state` / `PolicyState` | Scope pointer to active/pending versions, pending time and activation sequence |
| `subscription_operation_receipts` / `TrialReceipt` | Original Trial activation response and fingerprint; unique user/operation/key |
| `subscription_publication_receipts` / `PublicationReceipt` | Operator publication manifest replay receipt |
| `subscription_admin_publication_receipts` / `AdminPublicationReceipt` | Actor/operation/key, normalized fingerprint and accepted publication; unique actor/operation/key |
| `subscription_admin_configuration_state` / `AdminConfigurationState` | GLOBAL configuration revision for publication/scheduler serialization |
| `subscription_outbox` / `SubscriptionOutboxMessage` | Immutable message body and mutable recipient delivery/lease state; recipient/status/next-attempt/lease index |
| `subscription_message_receipts` / `SubscriptionMessageReceipt` | Committed consumer/hash receipt; unique message/consumer |
| `subscription_message_reviews` / `SubscriptionMessageReview` | Retained incompatible/contradictory results and reason; no automatic business override |

One purchase can bind one Payment transaction and one paid period. A purchase can have multiple historical refund requests but projects its latest reference/result. One successful payment creates at most one period. Trial uniqueness is lifetime, not only active. Product refund deadline is pinned during paid activation. Payment and Subscription lifecycle labels differ; current rights always resolve against the period's half-open UTC interval.

## Atomic persistence boundaries

| Operation | Writes that commit together |
| --- | --- |
| Trial activation | Trial flag/period, response receipt, entitlement sequence and Outbox |
| Purchase creation | Account revision guard, pinned purchase and checkout command Outbox |
| Payment result intake | Receipt, guarded purchase changes; success also Trial supersede, paid period, account sequence and entitlement Outbox |
| Refund request | Account serialization, retained request, purchase pending projection and refund command Outbox |
| Refund success intake | Receipt, matching request/purchase, period `REFUNDED`, sequence and changed entitlement Outbox |
| Publication/actual activation | Policy/plan/pointers as applicable, receipt/revision and actual policy fact Outbox |

Required MongoDB transactions are local to each owner; normal event consumers commit independently. Provider calls are outside these transactions. The explicit dev-release exception joins two module-owned writes in one local transaction; see its [runbook](../../runbooks/dev-purchase-reset.md). Unknown commits rely on replay/receipt resolution rather than resetting history.

## Neighbor-owned integration storage

These are boundary references, not Subscription-owned collections. Follow the [contracts](module-contract.md) before changing them.

| Owner | Collections and important constraints |
| --- | --- |
| Payment | `payment_transactions`: unique purchase and partial provider/reference; provider-work due index, pinned merchant/reference/date, diagnostic summary. `payment_refunds`: unique refund reference, partial provider attempt, one blocking and one successful full refund per payment, work due index |
| Payment delivery | `payment_outbox`, `payment_message_receipts`, `payment_message_reviews`; delivery-due and unique message/consumer indexes |
| Quota | `quota_policy_snapshots`: unique snapshot ID and scope/version; `quota_entitlement_projection`: unique user; `quota_usage_counters`: unique user/type; `quota_usage_ledger`: unique user/sourceFeature/key plus user/type/recorded-time lookup |
| Quota activation/delivery | `quota_policy_activation_receipts`: ordered scope head and per-activation/type receipts via partial unique indexes; `quota_outbox` and `quota_message_receipts` with delivery-due and unique message/consumer indexes |

Quota counters are keyed by user/type, not tier/version. Ledger records the accepted operation, amount, source/key/reference, server recording time, policy snapshot/version, allowance/window, used-after and limit-at-record; retries return this original accepted result. Counter and ledger commit together. Free activation preserves ledger and retains usage recorded since its effective instant when delayed. See [quota lifecycle](../../flows/subscription/quota-lifecycle.md).

There is no approved TTL/deletion policy for unresolved purchases, payments, refunds, messages or necessary receipts. Existing null entitlement fingerprints migrate lazily, preserving histories and counters; events lost before durable delivery are not reconstructed. Operator/account provisioning tools remain described in [tools/subscription](../../../tools/subscription/README.md).
