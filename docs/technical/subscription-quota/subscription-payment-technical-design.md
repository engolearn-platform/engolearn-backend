# Subscription and Payment technical design

P01-P02 source update (2026-10-30): contracts, core models and recipient-scoped durable delivery foundation are implemented, including existing Trial/Free publication and the Quota consumer. Public Payment business consumers and purchase/paid/refund/provider flows remain P03-P07 work. The rest of this document continues to describe the full target; source presence alone is not end-to-end payment verification. See [implementation status](implementation-status.md) and workspace `.artifacts/subscription-payment-p01-p02/` for actual evidence.

Status: **Agreed target design; P01-P02 foundation implemented, paid flows pending**. Revised 2026-10-30 from the read-only `D:/DownloadinD/subscription-payment-technical-design-draft.md`. This canonical English design records the approved decisions for incremental implementation on the `feat/subscription` baseline. Documentation approval is not authorization to implement, deploy, charge or refund.

## 1. Purpose, scope and actual baseline

Define ownership, contracts, state transitions and recovery for Premium purchase orchestration and Payment core in the existing Spring Boot modular monolith. Keep class names, physical schemas/index names, scheduler frequencies and operational tuning open until their implementation slice. No new broker, microservice, Maven module, cross-module transaction or automatic Saga compensation is required.

Current source has Subscription plans, accounts, periods, immutable snapshots, Trial activation and Admin configuration; Quota has usage enforcement and after-commit Subscription event consumers. Before P01-P02, Trial and scheduled Free-policy activation used in-process events and Payment had only the two immutable fact records documented in the [Payment handoff](payment-contract-handoff.md) and a compile-only example. The pre-P01/P02 snapshot had no checkout/purchase/refund runtime, paid activation, provider adapter or durable messaging. The dated source update above supersedes that delivery snapshot; paid/provider business flows remain absent. Record construction does not verify a payment. See [implementation status](implementation-status.md) for historic verification, which this document does not replace.

The target covers purchase intent, checkout setup, payment outcome handling, full-refund orchestration, purchase history, durable delivery and the Subscription-to-Quota transition. Provider-specific VNPay signing, checkout parameters, IPN, ReturnURL, QueryDR, refund API, credentials, public HTTPS callbacks and Sandbox/Production configuration belong to P07. Generic authorization, ownership validation and secret handling apply from the first slice. Provider fake adapters are **test fixtures only through P01-P06**; no fake checkout/success/refund adapter may be wired as a production capability while P07 is absent.

## 2. Ownership and dependency direction

| Owner | Responsibilities and owned data |
| --- | --- |
| Subscription | Plans, billing options, price/policy/benefit snapshots; PremiumPurchase; purchase eligibility/history; Trial/Premium periods; product refund eligibility and window; entitlement sequencing; command Outbox, result receipts and quota-event Outbox |
| Payment | PaymentTransaction, RefundTransaction, checkout/provider execution; financial/provider invariants and verified outcomes; command receipts and result Outbox |
| Quota | Entitlement projection, usage counters/ledger, calendar allowances, quota-event receipts and selective policy-activation resets |

Subscription may import `payment.contract` for Payment-owned command DTOs and result declarations. Commands can be semantically initiated by Subscription while their public types are owned by the receiving Payment capability. New command DTOs use `payment.contract.dto` and the `Dto` suffix; new past-tense event declarations use `contract.event` and the `Event` suffix. The existing two root records remain current declarations until P01 explicitly revises them. Names below identify message meanings, not already-declared Java classes. Payment must never depend on Subscription. Quota imports `subscription.contract`; Subscription never calls Quota. No feature reads another feature's collections or imports its internals; references are plain opaque values.

Subscription-to-Payment uses asynchronous durable commands, not a synchronous `createCheckout()` business call waiting for a session. Payment-to-Subscription uses durable verified result facts. Subscription-to-Quota uses durable entitlement/policy facts. An internal delivery adapter may call the recipient locally as transport **after** the producer transaction; it never joins the producer's transaction or becomes a request-response purchase contract. A broker adapter can replace this transport later without changing these semantics. See [communication policy](../../architecture/cross-feature-communication.md) and [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md).

## 3. Domain data and identities

| Model | Minimum logical fields and invariants |
| --- | --- |
| `PremiumPurchase` (Subscription) | Internal ID, unique opaque `purchaseReference`, `userId`, selected `planCode`/`billingPeriod`, pinned `amountVnd`/`currency`, `planVersion`/`policyVersion`, plan/benefit/policy/billing-option snapshots, refund policy and derived `refundEligibleUntil`, nullable `paymentId`/`periodId`, lifecycle state, refund projection/reference, request idempotency key/fingerprint, `blocksNewPurchase`, `createdAt`/`updatedAt`/`paidAt` |
| `PaymentTransaction` (Payment) | Unique `paymentId`, unique `purchaseReference` in MVP, `userId`, pinned `amountVnd`/`currency`, checkout state/URL, `createdAt`/`expiresAt`/`paidAt`, provider-owned references/verification details |
| `RefundTransaction` (Payment) | Internal `refundId`, unique stable `refundReference`, `paymentId`/`purchaseReference`/`userId`, original full amount/currency, `refundType=FULL`, state, authenticated `requestedBy`, reason and request/result timestamps |
| Outbox delivery | Stable message identity/type/schema, aggregate/correlation, immutable payload, recipient identity, delivery state, attempt/error metadata, next-attempt/lease timestamps, creation/delivery timestamps |
| Recipient receipt | Recipient identity plus stable message identity/type, aggregate/reference, processed time and outcome reference; unique within that consumer |

One purchase has at most one PaymentTransaction and activates at most one Premium period. A payment may retain failed refund attempts in history but may have at most one successful full refund. A refund reference denotes exactly one request. Reuse identities on retry; never generate a new reference while the previous outcome is pending or unknown. No cross-module object relationships or collection access are needed.

### Monetary semantics and compatibility

Target `amountVnd` is a non-negative integer count of VND units, represented without floating point; currency is exactly `VND` for this MVP. Snapshot the server-selected configured price when creating the purchase. Configuration changes cannot reprice an existing purchase or callback. No frontend/Admin amount is authoritative for purchase or full refund.

The existing `PaymentSucceeded.amount` is still declared as `long` **minor units** with range `0..9007199254740991`, and its currency is an uppercase ISO 4217 code. That declaration is not silently redefined here. P01 must explicitly revise records, compile example, tests, handoff and all callers together before a production consumer exists, with compatibility/migration assessment if persisted or external consumers are discovered. Only the Payment provider adapter converts provider wire encoding to/from canonical VND units, according to the verified provider specification. There is no general multiply/divide-by-100 rule for the existing field or VND; provider-specific scaling never leaks into Subscription or Quota.

## 4. Complete target message contracts

All messages have stable `messageId`, `messageType`, `schemaVersion` and UTC `occurredAt`/`createdAt`. For result facts, the public `eventId` is the stable message identity (do not create a second unrelated deduplication identity). Redelivery preserves identity and payload. Payment command/result contracts include `purchaseReference`; quota facts use user/activation correlation instead. Required strings are nonblank opaque IDs unless stated otherwise; timestamps use UTC `Instant`. No persistence entities, Spring implementation types, web DTOs or provider parameters appear in these messages. Payment contracts omit plan/policy snapshots; Quota contracts retain their typed policy snapshot/rules.

The table specifies required payload fields **in addition to the envelope**. Payment rows are target contracts; only the two current Payment shapes in the handoff exist today. Quota rows describe semantic requirements while retaining existing typed payloads: `EntitlementChangedEvent(EffectiveEntitlementDto entitlement)` includes full policy snapshot/rules, `policyVersion` and `policyActivationSequence`; `QuotaPolicyActivatedEvent` includes `policyVersion`, `activationSequence`, `changedQuotaTypes` and full `QuotaPolicySnapshotDto`. Add durable envelope/ordering support without replacing these with lossy ID-only schemas. Preserve their freshness/activation ordering and immutable snapshot data during migration.

| Direction / meaning | Required payload | Meaning / guard |
| --- | --- | --- |
| Subscription -> Payment `CreateCheckoutRequested` | `purchaseReference`, `userId`, `amountVnd`, `currency`, `expiresAt` | Server-pinned price; one PaymentTransaction per reference. Do not create a payable checkout from an expired delayed command; determine closure safely and emit `PaymentExpired` once definitively unpaid/closed, or retain unresolved state if a provider attempt may exist. |
| Subscription -> Payment `FullRefundRequested` | `refundReference`, `paymentId`, `purchaseReference`, `userId`, `requestedBy`, `reason` | Subscription has authorized a product-eligible full refund; Payment derives amount from original payment and enforces financial/provider invariants. |
| Payment -> Subscription `CheckoutReady` | `purchaseReference`, `paymentId`, `checkoutUrl`, `expiresAt` | Checkout persisted and usable; no payment success implied. |
| Payment -> Subscription `CheckoutRejected` | `purchaseReference`, `reasonCode`, `rejectedAt` | Definitive permanent setup rejection. A transient infrastructure failure is not rejection. |
| Payment -> Subscription `PaymentSucceeded` | `paymentId`, `purchaseReference`, `userId`, `amountVnd`, `currency`, `paidAt` | Verified payment; Subscription validates every correlation/price field against the pinned purchase before activation. |
| Payment -> Subscription `PaymentFailed` | `paymentId`, `purchaseReference`, `userId`, `failureCode`, `failedAt` | Definitive verified unsuccessful payment, never a timeout inference. |
| Payment -> Subscription `PaymentExpired` | `paymentId`, `purchaseReference`, `userId`, `expiredAt` | Payment confirms definitively closed/unpaid checkout after checking provider/attempt state; used for `PAYMENT_EXPIRED` and safe lock release. |
| Payment -> Subscription `PaymentRefundSucceeded` | `refundReference`, `refundId`, `paymentId`, `purchaseReference`, `userId`, `refundedAt` | Confirmed completed full refund for exactly the requested reference; the original payment determines amount. |
| Payment -> Subscription `PaymentRefundFailed` | `refundReference`, `refundId`, `paymentId`, `purchaseReference`, `userId`, `failureCode`, `failedAt` | Definitive rejection for exactly that refund attempt; no entitlement revocation. |
| Subscription -> Quota `EntitlementChanged` | `userId`, strictly monotonic `entitlementVersion`, effective `policyScope`, `policySnapshotId`, `effectiveAt`, `validUntil` for time-limited rights | Refresh applicable snapshot/projection; preserve same-calendar-window usage/allowance. |
| Subscription -> Quota `QuotaPolicyActivated` | Stable `activationId`, `policyScope`, `policySnapshotId`/`policyVersion`, changed `quotaTypes`, `effectiveAt` | Actual activation, not future configuration save; selective Free-policy reset only, preserving ledger and unaffected snapshots/types. |

Both refund result messages require the new `refundReference`; `refundId` alone cannot correlate a rejected/retried request safely. No new partial-refund contract is introduced. `vnp_*`, merchant IDs, HMAC, provider codes and IP addresses stay in Payment adapters.

## 5. Lifecycle, ordering and eligibility guards

### Purchase and Payment

Normal purchase flow: `PAYMENT_SETUP_PENDING` -> `PAYMENT_PENDING` -> `ACTIVE`. Definitive setup rejection becomes `PAYMENT_SETUP_FAILED`; verified failed payment becomes `PAYMENT_FAILED`; verified closed/unpaid checkout becomes `PAYMENT_EXPIRED`. A correlation/invariant conflict becomes `REQUIRES_REVIEW`, with no new entitlement granted. Unknown provider status may remain a waiting purchase projection; it must not be represented as a definitive failure.

Payment states: `CHECKOUT_SETUP_PENDING` -> `PENDING` or definitive `CHECKOUT_SETUP_FAILED`; `PENDING`/`UNKNOWN` -> verified `SUCCEEDED`, `FAILED` or `EXPIRED`; ambiguous outcomes -> `UNKNOWN`; inconsistent evidence -> `REVIEW_REQUIRED`. Setup errors with uncertain provider effects also require unresolved/reconciliation handling rather than false rejection. Success is never downgraded by late failure/expiry/setup facts.

`ACTIVATION_PENDING` describes processing **inside** the Subscription activation transaction in the baseline design. Receipt and all activation writes commit together, so this is not a separately committed durable purchase state. A crash rolls back the receipt and permits result redelivery; no durable receipt may suppress an uncommitted activation. If a future slice intentionally splits activation, it must first design a persisted retry job/state and its atomic receipt handoff. Do not claim such a worker exists now.

| Incoming fact / race | Required behavior |
| --- | --- |
| `CheckoutReady` in setup-pending | Bind the Payment ID once and expose URL/expiry; reject identity conflicts. |
| Success before `CheckoutReady` | A valid success may bind the previously absent Payment ID and activate atomically; a later matching checkout fact is a no-op for lifecycle. |
| Checkout ready/rejected/failed/expired after activation/refund | Never regress to pending/failure, reopen checkout, release an unresolved lock incorrectly or restore refunded rights; duplicate matching facts are no-ops, contradictory facts remain reviewable. |
| Duplicate success, including a different event ID | Receipt plus unique payment-to-period activation and state predicates prevent a second period, extension or entitlement version increment. |
| Success after a purported definitive failure/expiry | Preserve evidence and reconcile the contradiction; never discard a verified late result due to checkout age. Do not silently overwrite a newer purchase/active entitlement or grant overlapping Premium. Apply only when identities, eligibility and current state can be safely reconciled; otherwise retain review state for operations. |
| Correlation/user/price/currency/payment mismatch | No activation; preserve original state, payload/evidence and review/reconciliation work. Acknowledgment requires either valid application or durable explicit unresolved handling, never silent loss. |

Request idempotency is `(user, operation, Idempotency-Key)` plus normalized user-input fingerprint (`planCode`, `billingPeriod`, not a newly calculated price). Same key/body returns the original purchase; different body is `409 IDEMPOTENCY_CONFLICT`. Two different keys still compete for an atomic one-blocking-purchase-per-user constraint. Replayed accepted requests are resolved before new-purchase eligibility checks.

Payment command idempotency also checks the immutable business payload: the same `purchaseReference` and matching user/amount/currency/expiry reuse the existing transaction/result, never a second charge. The same reference with conflicting payload preserves the legitimate transaction and records a durable conflict for review; it must not emit a `CheckoutRejected` that unlocks the legitimate pending purchase. Apply the equivalent payload/identity check to stable refund references.

New purchases require no effective active Premium and no unresolved blocking purchase. Active Trial is allowed. Persisted lifecycle labels alone are insufficient: resolve Premium eligibility by effective period times. Pending setup/payment, unknown outcome, unresolved activation and review keep `blocksNewPurchase`. Definitive setup rejection, verified failure or confirmed unpaid expiry releases that pending-purchase lock atomically with the result. Successful activation also releases the pending-purchase lock atomically, but active Premium independently prevents another purchase. Releasing one purchase's lock must be conditional on its ownership and cannot clear a newer purchase's lock. Refund-pending/unknown must not bypass active-Premium eligibility or the unresolved refund guard. Ending/revoking Premium is not permission to ignore another unresolved purchase/refund.

### Checkout expiry and late results

`expiresAt` limits checkout use, not message delivery or result validity. Reaching local expiry, a callback timeout or a missing browser return never unlocks a purchase. Payment must wait for provider/attempt confirmation: confirmed closed/unpaid -> durable `PaymentExpired`; verified paid -> durable success/activation; ambiguous -> `UNKNOWN`, retain lock, reconcile and alert operations. No result Outbox record or receipt expires merely because the checkout expired. Preserve all unresolved state and late facts. Delivered-Outbox cleanup is a separate operational policy, with retention/replay safety agreed before deletion; this design sets no duration.

### Refund

Subscription checks Admin authority, purchase/period identity, pinned product refund policy and window before atomically creating the stable refund request/projection plus command Outbox. Existing business windows are seven days for monthly and thirty days for yearly purchases from `paidAt`; no new duration is introduced here. Payment checks the original transaction succeeded, identities match, the full original amount is refundable and provider constraints permit execution. It does not decide the product refund window.

Refund states: `REQUESTED` -> `PROCESSING` -> `SUCCEEDED` or definitive `FAILED`; timeout -> `UNKNOWN`, then verified reconciliation outcome. No new refund/reference is allowed while one is requested, processing or unknown, even under concurrent Admin requests. A successful full refund occurs at most once per payment. A definitively failed attempt may be retried only through a new authorized request after rechecking eligibility, while retaining the previous attempt history.

Subscription applies refund facts only to the matching stored `refundReference`, payment, purchase, user and bound refund identity. Duplicate success is a no-op. Late failure after success never restores access or overwrites `REFUNDED`; a result for an old failed attempt cannot clear a newer pending attempt. Unmatched or contradictory results require durable review, not revocation or a new refund. Only valid refund success marks the associated period `REFUNDED` and emits any effective entitlement change; a failed refund preserves the period's then-current lifecycle (including natural expiry), rather than blindly setting it `ACTIVE`. If refund and activation facts are reordered, keep a matching not-yet-applicable result durably pending/reviewable and reconcile before granting access; never temporarily resurrect a payment already known to be refunded.

## 6. Atomic Trial conversion, time and Quota

At verified `paidAt`, atomically supersede an active Trial as `SUPERSEDED`, preserve its history and lifetime trial-used flag, create exactly one Premium period from the purchase snapshot, update account/purchase, commit result receipt and append the entitlement Outbox fact. Premium begins at `paidAt`, never result-delivery time. No deferred paid conversion extends the purchased interval; if delivery is late enough that its period has ended, resolve current effective rights by time rather than falsely exposing active Premium.

Calculate monthly/yearly Premium end by adding one calendar month/year to `paidAt` in `Asia/Ho_Chi_Minh`, clamping an absent target day to that month's last day. For example, January 31 plus one month ends on February's last day; February 29 plus one year ends February 28 in a non-leap year. Preserve local time of day, use half-open `[paidAt, endsAt)` intervals and persist UTC instants. Do not replace this with fixed day counts. Trial is not reissued, paused or restored by conversion/refund. `OQ-SUB-F1-01` is resolved; business module rules `BL-29`/`BL-30` and registration rule `BR-F1.11` record the paidAt/calendar and Trial conversion decisions.

Durable entitlement sequencing must cover **all** Subscription writers: Trial activation/conversion, Premium activation/refund/expiry and other effective changes, including policy-driven projection freshness. Allocate a strictly increasing per-user `entitlementVersion` atomically with the effective state and its Outbox record, serializing competing writers. Lookup and emitted versions must share a comparable ordering model; the current composite user/policy freshness value must be explicitly reconciled with this target before migration. Timestamps or independent local counters cannot substitute for a common sequence. Quota receipts and projection conditional updates commit together and prevent older versions from overwriting newer rights.

P02 must migrate existing Trial entitlement and scheduled Free-policy activation publication to the same durable path, as well as future paid facts. There must not be a permanent parallel in-memory-only path that preserves the known missed-event gap. Existing synchronous effective-entitlement lookup remains a source-of-truth fallback for missing/expired projections; it is not durable replay and cannot recover a missed policy reset on its own.

Starting/ending/refunding a period or changing tier updates policy selection but **never resets current-calendar-window usage or allowance**. Quota windows remain daily/Monday-weekly/calendar-month in `Asia/Ho_Chi_Minh`, UTC stored. Scheduled Free-policy activation remains the distinct, selective reset cause already defined in the [Quota design](quota-engine-design.md); retain ledger, old snapshots and unaffected types. Durable delivery must preserve effective-time handling, activation ordering and idempotent reset receipts, not replay historical resets as repeated grants.

## 7. Durable delivery and local transaction boundaries

Producer commits owned business state and immutable Outbox records in one MongoDB transaction. Dispatcher claims deliveries with leases; recover abandoned work and retry technical failures. Consumer commits its receipt and owned state changes in its own local transaction. Mark a recipient's delivery complete only after commit acknowledgment or confirmed durable intake with a recovery obligation. Lost acknowledgment causes redelivery; receipts plus business uniqueness prevent repeated effects. No exactly-once transport promise is made.

Route by explicit message type and recipient. Delivery/receipt uniqueness includes the consumer identity, e.g. `(messageId, consumerId)`; a multi-recipient message needs separate delivery status/lease/acknowledgment per recipient. Payment may claim only checkout/refund commands addressed to Payment; Quota may claim only entitlement/policy facts addressed to Quota; Subscription may claim only Payment results addressed to Subscription. A Payment acknowledgment must never mark Quota work delivered, and the consumers cannot consume each other's messages. Shared technical dispatch primitives must have no feature/domain dependency and must not erase these ownership boundaries.

| Owner transaction | Atomic durable writes |
| --- | --- |
| Subscription purchase creation | Purchase + request idempotency data + owned user lock + checkout command Outbox |
| Subscription checkout/failure/expiry receipt | Receipt + guarded purchase projection/lock transition |
| Subscription payment success | Receipt + Trial supersede if applicable + Premium period/account/purchase + entitlement sequence + quota-event Outbox |
| Subscription refund request | Authorized stable request/projection + refund command Outbox |
| Subscription refund success | Receipt + guarded refund/purchase/period/account transition + effective entitlement sequence/Outbox if changed |
| Subscription existing Trial / Free activation | Existing state/activation writes + corresponding entitlement/policy Outbox; no after-commit-only gap |
| Payment command receipt | Receipt + PaymentTransaction in setup-pending, or RefundTransaction in requested state |
| Payment checkout/provider result | Guarded Payment/Refund state + corresponding result Outbox |
| Quota event receipt | Consumer receipt + ordered projection update, or selective activation counter/allowance changes and activation receipts, preserving ledger |

Provider network calls occur **outside** database transactions, with stable provider attempt identity. Persist intent before calling; record verified result and Outbox afterward. A provider timeout leaves an unknown outcome for reconciliation and must not cause a second charge/refund. If command receipt is committed before execution, owned durable work/state must make execution recoverable; a receipt alone is insufficient.

| Failure | Recovery |
| --- | --- |
| Producer commits, process exits before dispatch | Recover persisted Outbox and dispatch. |
| Consumer transaction fails / infrastructure unavailable | Roll back receipt and business writes; retry same delivery. |
| Consumer commits but acknowledgment is lost | Redeliver same identity; duplicate no-op, acknowledge committed outcome. |
| Payment saved setup/refund intent but provider call is uncertain | Keep pending/unknown recoverable work, query/reconcile same attempt before further execution. |
| Permanent invalid command | Persist explicit rejection/result and receipt; do not retry unchanged invalid business input forever. |
| Contradictory/unapplicable result | Retain payload and unresolved review/reconciliation state; do not grant rights or silently acknowledge loss. |

Use forward recovery; never delete a purchase because Payment is temporarily unavailable. Admin refunds are separate authorized business operations, not automatic compensation. Retry backoff/budget, alert threshold, lease duration, scheduler rate, dead-letter/manual replay procedure, physical schema and cleanup retention remain implementation/operations decisions with no constants introduced here. Do not TTL unresolved purchases, payments, refunds, result messages or necessary deduplication receipts.

## 8. Proposed HTTP and security contracts

These endpoints are **proposed**, absent from current runtime. Frontend calls Subscription only; provider callback/return adapters are a separate Payment concern in P07.

| Method and path | Target behavior |
| --- | --- |
| `POST /api/v1/subscription/me/purchases` | Authenticated user, `Idempotency-Key`, body `{ "planCode": "PREMIUM", "billingPeriod": "MONTHLY" }`; server eligibility and price snapshot; new `201` purchase with reference, pinned amount/currency, setup-pending state and polling details. Replay returns same purchase, without a new command. |
| `GET /api/v1/subscription/me/purchases/{purchaseReference}` | Owner-only current state/projection; no checkout URL during setup. Matching ready state exposes payment ID, URL and expiry. Expired URLs must not be offered as payable while outcome remains unresolved. |
| `GET /api/v1/subscription/me/purchases` | Owner-only history: one entry per purchase including pending/failed attempts and received payment/refund projections; never query Payment collections. |
| `POST /api/v1/subscription/admin/purchases/{purchaseReference}/refund` | ADMIN-only full refund with authenticated actor/reason; no client amount or owner override; atomically authorize and enqueue once. |

Use typed web request/response DTOs, `ApiResponse` success envelopes and `ProblemDetail` failures according to backend conventions. Polling is the initial UI mechanism; cadence, UI timeout/backoff and detailed failure shapes are implementation review items. No WebSocket/SSE is required. Authorize every detail/history request against the principal; derive user/actor from authentication, not body. No client success flag, paid amount, browser redirect or ReturnURL proves payment. Never log credentials, signatures, tokens or full sensitive provider payloads. VNPay verification of signature/amount/status, merchant credentials and callback security must be designed and validated in P07.

## 9. Proposed implementation slices

P01-P07 are reviewable future work, in the order below. Each slice must preserve existing behavior until its new boundary is complete. Fake provider adapters remain test-only until P07; no production readiness is implied by core tests.

| Slice | Dependencies | Deliverables | Behavioral acceptance |
| --- | --- | --- | --- |
| P01 Contract and core models | Approved business rules and this design | Payment-owned command/result declarations, explicit `amountVnd` contract revision, refundReference on both results, Purchase/Payment/Refund models/states and uniqueness/ownership plan | Required fields/units/correlation and examples agree; no Subscription dependency in Payment; no fake production runtime; existing record migration assessed. |
| P02 Durable foundation | P01 | Module-owned Outboxes/receipts, recipient-scoped dispatcher/leases, local transactions, monotonic entitlement sequence migration, existing Trial and Free-activation durable publication/consumption | Crash after producer commit is recoverable; lost ack is duplicate-safe; receipt rollback works; concurrent Payment/Quota dispatch never steals or completes the other's delivery; no old Trial/Free non-durable path remains; stale versions/resets cannot reapply. |
| P03 Purchase orchestration | P01-P02 | Proposed create/detail APIs, pinned price/snapshot, request idempotency, atomic one-blocking-purchase guard, checkout command and ready/rejected/expiry handling | Same key/body replays; changed body conflicts; two keys race safely; active Premium blocks and Trial is allowed; expiry alone keeps lock; test-only adapter drives definitive/unknown paths without enabling real payments. |
| P04 Verified paid activation | P01-P03 | Payment success/failure consumption, atomic Trial supersede/Premium activation/receipt/sequence/Outbox, ordering/review guards | Success-before-checkout, duplicate success and lost ack create one period; late failure never downgrades; mismatch grants nothing; paidAt/calendar clamp correct including late arrival; same-window usage retained. |
| P05 Purchase history | P03-P04 | Owner-scoped list/detail projection, complete pending/failed/refund-aware history and polling contract | One record per intent; user isolation; no Payment collection access; replay/late facts do not duplicate history or expose expired checkout as payable. |
| P06 Refund core | P01-P05 | Admin product eligibility/window check, stable full-refund request, Payment financial invariants, RefundTransaction and result consumers | Concurrent/repeated pending requests create one attempt; UNKNOWN blocks duplicates; one successful full refund per payment; refundReference isolates reordered attempts; only success revokes, failure preserves current period; entitlement delivery durable. |
| P07 VNPay integration | P01-P06 and provider/security decisions | Real provider adapter, signed checkout, verified IPN, UX ReturnURL, QueryDR/reconciliation, refund API, credential/configuration and Sandbox end-to-end validation | Verify real authoritative outcomes, amount encoding at adapter boundary, timeout/expiry reconciliation, late results and refund safety; browser return alone never activates. Production rollout needs separate evidence/authorization. |

Do not start all slices in parallel. Review P01 contracts and dependencies before persistence/flow implementation. No application implementation is included in this documentation synchronization.

## 10. Verification and unresolved decisions

Future behavioral checks must cover request/command idempotency with payload mismatch; business uniqueness under concurrent different keys; receipt/state/Outbox atomic rollback; producer crash/lost acknowledgment; recipient-scoped routing; reordered checkout/payment/refund facts; unknown/expired provider outcomes and late success; active-Premium eligibility and lock ownership; Trial history and calendar month/year clamps; unchanged quota usage on tier changes; durable existing Trial/Free events; monotonic concurrent entitlement updates; selective policy reset idempotency; owner/Admin authorization; and architecture boundaries. Use replica-set Mongo for transaction evidence. Compile-only Payment examples are not lifecycle tests.

Existing whole-project verification has known Topic/context failures recorded in [status](implementation-status.md). Do not claim a green build from old scoped evidence or from this documentation change. No Maven suite is required for documentation-only synchronization; validate scope, content and local links instead.

| Item | Status |
| --- | --- |
| Trial termination (`OQ-SUB-F1-01`) | Resolved: atomic supersede at paidAt; history and lifetime-used preserved. |
| Delivery baseline / refundReference / checkout expiry / calendar arithmetic | Agreed target here and ADR-0002; unimplemented. |
| Payment methods, displayed provider details and return UX (`OQ-SUB-F1-02`) | Open for provider integration/product review; not a reason to invent core policy. |
| Polling UX, physical schema/index names, contract evolution mechanics and exact internal adapters | Specify during relevant slices while preserving these invariants. |
| Lease/retry/alert/replay/cleanup tuning and retention | Open operations decisions; no checkout-based deletion or invented duration. |
| Entitlement version migration and population-scale activation capacity | Implementation design/evidence required before durable migration or large rollout; current composite freshness cannot be assumed to satisfy the target sequence. |

## 11. Local source references

- [Business module](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/00-module.md), [Premium overview](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/f1-premium-registration/README.md), [Premium rules](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/f1-premium-registration/rules.md), [purchase use case](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/f1-premium-registration/SUB-UC-003_dang-ky-premium.md), [history use case](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/f1-premium-registration/SUB-UC-007_xem-lich-su-mua-goi.md).
- [Payment handoff](payment-contract-handoff.md), [Quota design](quota-engine-design.md), [status](implementation-status.md), [agent handoff](AGENT-HANDOFF.md), [package rules](../../architecture/package-structure.md), [implementation conventions](../../architecture/implementation-conventions.md).
- [Subscription service](../../../src/main/java/fit/iuh/engolearn/subscription/service/SubscriptionService.java), [current payment success record](../../../src/main/java/fit/iuh/engolearn/payment/contract/event/PaymentSucceededEvent.java), [current refund success record](../../../src/main/java/fit/iuh/engolearn/payment/contract/event/PaymentRefundSucceededEvent.java).
