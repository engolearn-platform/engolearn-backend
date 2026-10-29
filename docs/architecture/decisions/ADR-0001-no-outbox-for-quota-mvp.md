# ADR-0001: Defer Transactional Outbox for Subscription–Quota Events

- **Status:** Accepted for MVP
- **Date:** 2026-10-29
- **Scope:** Current single-application modular monolith

## Context

Subscription owns quota policy and entitlement lifecycle. Quota reacts to two distinct Subscription contract events:

- `EntitlementChanged`: refresh the user's applicable quota scope/snapshot; do not reset usage.
- `QuotaPolicyActivated`: start a new allowance for the affected scope and quota types; preserve usage history.

The modules currently run in the same Spring Boot application. Durable asynchronous messaging is not required for the MVP, and the team accepts the operational limitation of in-process delivery.

## Decision

1. Publish the event types from `subscription.contract`; handle them in-process after the Subscription transaction commits.
2. Do **not** implement Transactional Outbox, Kafka, or a separate message broker in the MVP.
3. Keep Subscription as the source of truth for effective policy snapshots and current entitlement state. Quota stores the local projection and policy-activation receipts; it does not read Subscription collections directly.
4. Do **not** implement startup reconciliation or replay solely to recover missed in-process events in the MVP. If Subscription commits but Quota fails before committing the corresponding event-handler transaction, accept that an entitlement projection update or policy reset may be missed. This is an explicit MVP risk, not a condition that the implementation must self-heal.
5. Event consumers must be idempotent. A repeated `EntitlementChanged` must not reset usage; a repeated `QuotaPolicyActivated` must not create multiple allowance resets. Apply all quota types for one activation and write their receipts in one MongoDB transaction; receipts protect against duplicate delivery, not missed delivery.
6. This decision does **not** weaken the atomicity requirement for each usage operation: its counter update and ledger append remain in one MongoDB transaction.

## Consequences

- Simpler MVP implementation, with no outbox collection, polling worker, or broker.
- In-process events are not durable. A process failure can interrupt event handling and there is no guaranteed startup replay or automatic recovery.
- Quota may continue using its last successfully applied projection/allowance after a missed event. A stale limit or missed policy reset is an accepted MVP risk.
- Activation receipts only make duplicate delivery idempotent; they do not act as an event queue or detect missed events.

## Revisit when

Revisit this ADR before extracting either module into a separate service, when durable delivery/retries become a requirement, or when the accepted risk of stale entitlement/allowance state is no longer acceptable.
