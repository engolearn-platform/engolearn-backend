# ADR-0002: Durable Subscription, Payment and Quota delivery

Implementation status updated 2026-11-05: durable delivery now covers existing Trial/Free facts, checkout/refund commands and Payment results. Paid activation, refund core and the optional VNPay worker are implemented. This records source status, not new runtime verification. No historical lost-event backfill is claimed.

- **Status:** Accepted; core durable routes implemented
- **Date:** 2026-10-30
- **Scope:** Purchase/refund core and Subscription-to-Quota publication in the modular monolith
- **Supersedes:** [ADR-0001](ADR-0001-no-outbox-for-quota-mvp.md) for future implementation; its historical body and current runtime evidence are preserved

## Context

Purchase commands, verified payment/refund outcomes and entitlement/policy effects
must survive producer crashes, consumer rollback and lost acknowledgments. The
historical after-commit-only Subscription-to-Quota path could lose an event despite
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

The core persistence, recipient-scoped delivery, recovery workers and guarded paid/refund consumers now implement this decision. Operational financial review/replay UI, retention policy and population-scale evidence remain separate work. Accepting this ADR did not itself implement or verify runtime behavior; the original P01-P07 plan is preserved as history.

The [current module contract](../../technical/subscription/module-contract.md) documents implemented delivery and ownership. This ADR retains the architectural decision; the original technical design and proposed acceptance slices are available in Git history at `docs/technical/subscription-quota/subscription-payment-technical-design.md`. See the [current status and limits](../../technical/subscription/README.md) for provider verification distinctions.
