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
3. Keep Subscription's effective policy snapshots and current entitlement state as the durable source of truth. Quota stores projection state and idempotent policy-activation receipts.
4. Reconcile Quota projections and any missed effective policy activations from Subscription's current persisted state on application startup, before serving quota decisions. If an activation handler fails while the app is running, retry/reconcile the affected scope before allowing quota decisions against stale policy.
5. Event consumers must be idempotent. A repeated `EntitlementChanged` must not reset usage; a repeated `QuotaPolicyActivated` must not create multiple allowance resets.
6. This decision does **not** weaken the atomicity requirement for each usage operation: its counter update and ledger append remain in one MongoDB transaction.

## Consequences

- Simpler MVP implementation, with no outbox collection, polling worker, or broker.
- In-process events are not durable. A process failure can interrupt event handling; recovery relies on reconciling from Subscription's persisted state.
- Policy/entitlement projections may be temporarily stale after a handler failure. The application must not silently continue quota decisions for an affected stale scope.
- The reconciliation source/contract is part of the Quota design review; an agent must not invent a direct cross-feature collection read to implement it.

## Revisit when

Revisit this ADR before extracting either module into a separate service, when persistent event delivery/retries become a requirement, or when the same-application reconciliation cannot keep policy state sufficiently correct.
