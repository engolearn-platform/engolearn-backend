# Subscription integration contracts

Package/dependency policy is owned by [cross-feature communication](../../architecture/cross-feature-communication.md). This page describes implemented Subscription boundary semantics and the Payment/Quota capabilities used by the pilot. A DTO constructor validates shape; it does not authenticate a provider result.

## Payment-owned contracts

Subscription imports `payment.contract.dto` commands and `payment.contract.event` results; Payment never imports Subscription. [Contract source](../../../src/main/java/fit/iuh/engolearn/payment/contract) is authoritative for exact record fields. Commands include `messageId`, `schemaVersion=1`, `occurredAt`; results include `eventId`, version and occurrence time. Payment's payload ID/time agrees with its transport envelope.

| Type | Direction | Business fields beyond metadata |
| --- | --- | --- |
| `CreateCheckoutCommandDto` | Subscription to Payment | purchaseReference, userId, amountVnd, currency, expiresAt, optional clientIp |
| `FullRefundCommandDto` | Subscription to Payment | refundReference, paymentId, purchaseReference, userId, requestedBy, reason |
| `CheckoutReadyEvent` | Payment to Subscription | purchaseReference, paymentId, checkoutUrl, expiresAt |
| `CheckoutRejectedEvent` | Payment to Subscription | purchaseReference, reasonCode, rejectedAt |
| `PaymentSucceededEvent` | Payment to Subscription | paymentId, purchaseReference, userId, amountVnd, currency, paidAt |
| `PaymentFailedEvent` | Payment to Subscription | paymentId, purchaseReference, userId, failureCode, failedAt |
| `PaymentExpiredEvent` | Payment to Subscription | paymentId, purchaseReference, userId, expiredAt |
| `PaymentRefundSucceededEvent` | Payment to Subscription | refundReference, refundId, paymentId, purchaseReference, userId, refundedAt |
| `PaymentRefundFailedEvent` | Payment to Subscription | refundReference, refundId, paymentId, purchaseReference, userId, failureCode, failedAt |

`amountVnd` is an integer VND `long` in `0..9007199254740991`, currency exactly `VND`. Provider scaling belongs to Payment's VNPay adapter. Refund full amount is derived from the successful original payment. Client IP is captured from HTTP remote address; old queued commands can deserialize without it, but VNPay safely rejects unsent checkout lacking it rather than inventing an IP. Shape-compatible old constructors do not establish runtime readiness.

Payment command intake deduplicates transport identity and business reference. Different payloads for the same identity/reference are retained for review, not applied over accepted work. Subscription checks owner/reference, payment/refund identities, money and times before state mutation. Late/contradictory results cannot overwrite success or a newer refund; durable review records preserve inapplicable facts. Outcomes are trusted internal service capabilities invoked only after provider verification; there is no frontend success command.

`DevPaymentReset` is a conditional dev-only synchronous exception: Subscription checks source eligibility while Payment owns financial eligibility/audit, in one Mongo transaction. It emits no financial outcome. See [DEV-PAYMENT-RESET-01](../../runbooks/dev-purchase-reset.md).

## Subscription-owned contracts

| Capability / fact | Consumer | Meaning |
| --- | --- | --- |
| `SubscriptionAccountInitialization.initializeNewAccount(userId)` | User registration | Requires the caller's Mongo transaction. Creates NEW_ACCOUNT history with unused Trial and zero revisions. Existing valid history is preserved. Missing legacy provenance fails unavailable. Failure rolls back registration together with user/profile writes. |
| `EffectiveEntitlementProvider.resolveEffectiveEntitlement(userId, at)` | Quota | Effective policy at server time; returns `EffectiveEntitlementDto`, fails unavailable instead of assuming Free |
| `EntitlementChangedEvent` | Quota | New effective entitlement snapshot/version; refresh selection without usage reset |
| `QuotaPolicyActivatedEvent` | Quota | Stable activation ID/sequence, scope/version/snapshot, changed types and effective time; actual activation, not future save |

The [entitlement DTO](../../../src/main/java/fit/iuh/engolearn/subscription/contract/dto/EffectiveEntitlementDto.java) carries user ID, monotonic entitlement version, policy scope/snapshot ID/version, activation sequence, `effectiveAt`, nullable `validUntil` and full `QuotaPolicySnapshotDto`. Each rule carries quota type/display name/limit/unit/period/display order. Missing or expired projections use authoritative lookup; stale versions cannot replace newer ones. Lookup may advance Subscription's fingerprint/version and emit a durable entitlement fact. It does not replay a missed policy activation.

## Quota-owned capabilities

These in-process contracts in `quota.contract` have no frontend write endpoint. Current product producers are not integrated; the following is the contract future callers must obey.

| Capability | Semantics |
| --- | --- |
| `QuotaAvailability.checkAvailability(userId, quotaType)` | Snapshot without reservation or counter/ledger writes; conditional projection refresh allowed |
| `QuotaConsumption.consume(UsageCommandDto)` | Atomically enforce known amount and record once; denial creates no accepted usage ledger |
| `QuotaUsageRecording.recordUsage(UsageCommandDto)` | Record actual amount once even beyond limit; subsequent availability reflects overrun |

`UsageCommandDto` has userId, quotaType, positive amount up to `9007199254740991`, sourceFeature, stable idempotencyKey, optional sourceReference/occurredAt. Deduplication is `(userId, sourceFeature, key)`; operation/type/amount/reference/occurredAt must agree. Accepted replay returns original ledger ID, amount, recorded time, allowance and availability, even after a boundary. `occurredAt` does not select the window. `QuotaIdempotencyConflictException` signals changed payload; unavailable lookup/storage fails `QuotaUnavailableException`. Shape errors are illegal arguments.

`consume`/`recordUsage` join an existing caller transaction or create a local one, with bounded conflict recovery. Future Proficiency must commit attempt, timer, counter and ledger together; prepare external resources before opening that transaction. Speaking must record measured seconds per audio turn under one stable key, wholly assigned to its first accepted server-time window. Unit and period for each quota type must remain consistent across policy versions/scopes. No bonus, rollover or manual-reset capability is implemented.

## Delivery and transactions

| Recipient | Producer Outbox | Consumer effect |
| --- | --- | --- |
| `payment.checkout` | Subscription `CREATE_CHECKOUT` | Payment receipt and setup intent |
| `payment.refund` | Subscription `FULL_REFUND` | Payment receipt and financially guarded refund intent |
| `subscription.payment` | Payment checkout/payment/refund result types | Subscription receipt and guarded purchase/period/refund effects |
| `quota.subscription` | Subscription entitlement/policy facts | Quota receipt and ordered projection/reset effects |

Module-owned Outboxes implement the shared technical repository contract. The dispatcher claims only registered recipients, uses lease tokens/fencing, calls the consumer, then acknowledges after commit. Expired claims recover; lost acknowledgment redelivers. Technical errors schedule exponential retry capped at multiplier 64; consumer/hash receipts and business uniqueness prevent duplicate effects. Permanent business conflicts commit review/rejection evidence rather than retrying an unchanged invalid command indefinitely. Receipt does not mean the reviewed financial conflict was resolved.

Normal producer state/Outbox and consumer receipt/effect each commit in a local owner transaction. Provider work uses separate persisted leased/fenced state and calls the network outside database transactions. Checkout expiry never expires transport, receipts or unresolved work. There is no broker or automatic historical backfill. See [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md), [data atomicity](data-model.md#atomic-persistence-boundaries) and [VNPay recovery](../../runbooks/vnpay.md).
