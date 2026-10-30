# ADR-0002: Durable Subscription, Payment and Quota delivery

P02 source update (2026-10-30): durable technical delivery and the existing Subscription-to-Quota route are now implemented. Payment command/result transport storage is present but business consumers remain future work. The decision below still governs later paid-flow implementation. No historical lost-event backfill is claimed.

- **Status:** Accepted target design; not implemented
- **Date:** 2026-10-30
- **Scope:** Future purchase/refund core and existing Subscription-to-Quota publication in the modular monolith
- **Supersedes:** [ADR-0001](ADR-0001-no-outbox-for-quota-mvp.md) for future implementation; its historical body and current runtime evidence are preserved

## Context

Purchase commands, verified payment/refund outcomes and entitlement/policy effects
must survive producer crashes, consumer rollback and lost acknowledgments. The
current after-commit-only Subscription-to-Quota path can lose an event despite
durable duplicate-detection receipts. Paid activation must not add a second delivery
standard while existing Trial and scheduled Free-policy activation remain lossy.

## Decision

1. Use durable Outbox delivery for Subscription -> Payment commands, Payment ->
   Subscription results and Subscription -> Quota entitlement/policy facts. Migrate
   existing Trial and scheduled Free-policy events to this path, with no permanent
   parallel in-memory-only publication. No broker or cross-module transaction is required.
2. Each producer commits owned state and Outbox records together. Each consumer
   commits its own receipt and effects together. Dispatch occurs outside producer
   transactions; provider network calls occur outside database transactions.
3. Deliver at least once using recoverable leases/retries. Track delivery and receipts
   per recipient, e.g. `(messageId, consumerId)`. Payment and Quota cannot claim or
   acknowledge each other's deliveries. Acknowledgment follows committed effects or
   durable intake with an explicit recovery obligation. Lost acknowledgments redeliver.
4. Preserve stable message/business identities, uniqueness and guarded transitions.
   Atomically sequence per-user entitlement versions across all writers; stale facts
   cannot overwrite newer projections. Tier/period changes never reset same-window
   usage. Selective Free-policy activation resets remain distinct and idempotent.
5. Treat checkout expiry as a business deadline, not transport expiry. Wait for Payment
   confirmation: definitively closed/unpaid emits `PaymentExpired`, success activates
   from paidAt, ambiguity stays `UNKNOWN` with lock/reconciliation/operations attention.
   Never discard late results, unresolved state or deduplication receipts on checkout
   timeout. Delivered-Outbox cleanup requires a separate retention/replay policy.
6. Keep dependency direction Subscription -> `payment.contract` and Quota ->
   `subscription.contract`. Payment never depends on Subscription. Payment may own
   the command DTO types consumed by its capability as well as its result facts.

## Consequences and implementation boundary

Future work adds persistence, worker recovery, operational review and recipient-scoped
delivery. It must prove crash/lost-ack behavior, local atomicity, routing isolation,
ordering and duplicate safety. No retry/lease/retention constant is selected here.
No startup replay, Outbox or paid runtime is created by accepting this ADR. Current
in-process limitations remain until the migration is implemented and verified.

The [canonical technical design](../../technical/subscription-quota/subscription-payment-technical-design.md)
defines complete contracts, product/financial ownership, atomic Trial conversion,
calendar arithmetic, state guards and proposed P01-P07 acceptance criteria. P02
includes existing Trial and Free-policy delivery migration; fake provider adapters
remain test-only until P07.
