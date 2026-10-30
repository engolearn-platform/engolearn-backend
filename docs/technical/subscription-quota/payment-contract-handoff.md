# Payment contract handoff

Updated 2026-10-30 for P01-P02. Contracts and persistence/delivery foundation exist; this does not implement charging, checkout, purchase APIs, paid activation, refunds or VNPay.

## Current public types

Command DTOs are Payment-owned in `payment.contract.dto`; result facts are in `payment.contract.event`. Subscription may import these contracts. Payment never imports Subscription. The former root-package declaration-only records have been replaced; their only code caller was the compile-only example, updated in the same change. No existing Payment persistence or external production consumer was found.

Every command has `messageId`, `schemaVersion=1`, `occurredAt`; every event has `eventId`, `schemaVersion=1`, `occurredAt`. Transport uses that same identity without minting an unrelated event ID. Required IDs/text/timestamps and the schema version are validated by immutable record constructors; construction validates shape, not provider authenticity.

| Type | Payload in addition to metadata |
| --- | --- |
| `CreateCheckoutCommandDto` | purchaseReference, userId, amountVnd, currency, expiresAt |
| `FullRefundCommandDto` | refundReference, paymentId, purchaseReference, userId, requestedBy, reason |
| `CheckoutReadyEvent` | purchaseReference, paymentId, checkoutUrl, expiresAt |
| `CheckoutRejectedEvent` | purchaseReference, reasonCode, rejectedAt |
| `PaymentSucceededEvent` | paymentId, purchaseReference, userId, amountVnd, currency, paidAt |
| `PaymentFailedEvent` | paymentId, purchaseReference, userId, failureCode, failedAt |
| `PaymentExpiredEvent` | paymentId, purchaseReference, userId, expiredAt |
| `PaymentRefundSucceededEvent` | refundReference, refundId, paymentId, purchaseReference, userId, refundedAt |
| `PaymentRefundFailedEvent` | refundReference, refundId, paymentId, purchaseReference, userId, failureCode, failedAt |

`amountVnd` is an integer VND amount (`long`, 0..9007199254740991); currency is exactly VND for this MVP. The former `amount` minor-unit declaration is not silently reused. Provider wire scaling belongs only in the future verified provider adapter; do not apply an assumed factor of 100. Refund amount comes from the original verified payment, never an Admin body.

## Persistence and delivery foundation

Purchase/Payment/Refund models and unique indexes enforce business references, one blocking purchase per user, one PaymentTransaction per purchase, one pending refund and one successful full refund per payment. Period payment identity is unique when present. These constraints do not substitute for future authorized state-transition services.

Each module owns its Outbox/receipt collections. Shared technical dispatch claims only registered recipient routes, leases work with fencing tokens, retries technical errors and acknowledges after consumer commit. Future Payment command/result routes have no runtime business consumer yet and must not be acknowledged by placeholders. Existing Trial and Free-policy events do use durable delivery to Quota. No broker, TTL or cross-module transaction is introduced.

## Future business responsibilities

Subscription owns pinned prices/snapshots, purchase eligibility and product refund windows. Payment owns provider verification, financial/provider invariants and authoritative outcomes. Subscription consumes validated facts and owns period mutations. Browser returns cannot establish success. Checkout timeout alone never releases a purchase lock; confirmed failure/closed-unpaid expiry does, while UNKNOWN waits for provider reconciliation. Preserve late results and match refundReference to the correct request.

Trial conversion at verified paidAt is resolved: Trial becomes SUPERSEDED, history remains, Premium starts at the same instant with calendar month/year duration. Quota usage is retained in its calendar window. These paid transitions remain future implementation. OQ-SUB-F1-02 (methods, displayed details and return UX) remains open for P07.

## Evidence boundary

`src/test/java/fit/iuh/engolearn/payment/contract/PaymentContractConsumerExample.java` remains compile-only; it proves no lifecycle behavior. New P01-P02 critical checks and startup evidence are recorded in workspace `.artifacts/subscription-payment-p01-p02/` and [implementation status](implementation-status.md). Earlier 2026-10-29 scoped results were historical compilation/architecture evidence for the old declarations; they are not evidence for this revision. The known full-project Topic/context failures were not fixed by this work.

[Canonical design and proposed P01-P07](subscription-payment-technical-design.md), [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md), [business module](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/00-module.md).
