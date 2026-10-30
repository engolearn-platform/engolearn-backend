# Payment fact contract handoff

## Delivered boundary

M3 preparation declares `payment.contract.PaymentSucceeded` and
`payment.contract.PaymentRefundSucceeded` as immutable JDK records. Payment owns
these facts. They contain no validation, framework annotations, publisher,
listener, adapter, persistence, provider integration or paid lifecycle behavior.
Record construction does not establish that its contents are valid or verified.

The approved future dependency is Subscription -> `payment.contract`. Payment
must not depend on Subscription. Subscription will own its future consumer and
all period/snapshot mutations. Quota -> `subscription.contract` is unchanged;
these declarations introduce no runtime integration.

## Declared shape

Every field is required. Strings remain strings rather than parsing UUIDs or
interpreting opaque identifiers inside the records.

| Fact | Field | JDK type | Required meaning |
| --- | --- | --- | --- |
| Both | `eventId` | `String` | UUID string identifying the fact |
| Both | `paymentId` | `String` | Opaque nonblank Payment transaction identity |
| Both | `purchaseReference` | `String` | Opaque nonblank correlation identity owned by future Subscription purchase orchestration |
| Both | `userId` | `String` | Opaque nonblank account identity |
| Success | `amount` | `long` | Integer minor units, inclusive range 0..9007199254740991 |
| Success | `currency` | `String` | Uppercase ISO 4217 currency code |
| Success | `paidAt` | `Instant` | Verified payment completion time in UTC |
| Full refund | `refundId` | `String` | Opaque nonblank completed refund identity |
| Full refund | `refundedAt` | `Instant` | Completed full-refund time in UTC |

No purchase reference is minted or persisted in this increment. The example
amount in test source is illustrative, not a configured offer or production price.

## Future integration responsibilities

Payment may publish success only after verifying the transaction. Browser input
and return URLs cannot establish authoritative payment success. Payment owns
provider details and refund eligibility/window enforcement. Refund success means
a completed full refund for the correlated Premium purchase; partial refunds are
not introduced.

The future consumer must validate the required fields and verify the purchase
reference, user and payment identities against the pinned price and snapshot.
It must handle at-least-once and possibly reordered facts, deduplicate event and
transaction identities, and commit each applicable lifecycle transition once.
These are future implementation obligations, not guarantees of these records.
No durable delivery, reconciliation or retry mechanism is provided here.
Timestamps are facts, not instructions to extend access. No new paid entitlement
event is declared; the existing Subscription entitlement event remains the
intended fact after the real lifecycle behavior is specified and implemented.

The business baseline remains authoritative: Payment confirms outcomes,
Subscription owns periods and snapshots, and resource-owning modules decide
resource access. This handoff adds no activation, refund-period, quota-reset or
resource-access rules.

## Readiness and evidence limits

`src/test/java/fit/iuh/engolearn/payment/contract/PaymentContractConsumerExample.java`
is a compile-only exchange example using the records and JDK consumers, without
internal models or web DTOs. Its compilation is included in the shared backend
test batch. It contains no test or claim of activation/refund correctness.
Actual compilation and architecture results belong in the coordinated execution
report; source presence alone is not a passing result.

Observed on2026-10-29: the coordinated scoped batch passed66tests including9 architecture checks, and PaymentContractConsumerExample.class was present under target/test-classes with the current compilation timestamp. This confirms compilation and architecture compatibility only. The final whole-project verify still failed in the existing Topic/context areas; see implementation-status.md and the workspace execution log for exact counts.

Real Payment, charging, callbacks, purchase orchestration, delivery, idempotency,
paid transitions, refund handling and reconciliation remain unimplemented and
unverified by this increment. Two F1 questions still block real paid lifecycle
implementation: `OQ-SUB-F1-01` (how Trial ends on paid conversion) and
`OQ-SUB-F1-02` (payment methods, displayed transaction details and return flow).
This declaration does not resolve either question.

Business references: the sibling business repository's
`docs/usecase_spec/subscription/00-module.md` and
`docs/usecase_spec/subscription/f1-premium-registration/{README.md,rules.md}`.
Approved implementation and verification scope:
`.artifacts/subscription-payment-admin/{plan.md,test-plan.md}` in the workspace.
