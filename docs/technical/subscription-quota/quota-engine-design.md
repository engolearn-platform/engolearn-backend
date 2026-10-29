# Quota Engine — Technical Design (Draft)

> Status: **Draft for review**. This document describes the proposed MVP design; it is not an implementation approval until the open items at the end are reviewed.
>
> Scope: Subscription/Quota backend contracts, MongoDB usage data, event handling, consistency, and the narrow REST read APIs needed by the package/usage UI.

## 1. Goals and boundaries

Build a generic Quota feature that accepts configured quota rules and usage amounts without knowing what a product feature does.

- **Subscription owns** plan/subscription lifecycle, quota configuration versions/snapshots, policy scope, and effective times.
- **Quota owns** quota enforcement, usage counters, append-only usage ledger, calendar-window calculations, and policy-activation reset handling.
- **Speaking / Proficiency / future AI Chat own** the business action and decide when/how much usage occurred. They call Quota through `quota.contract`; they do not read or update quota collections.
- **Topic Learning and Assessment own resource-access rules**. Quota does not decide lifetime content access or resource-scoped durable access.
- Use the existing feature-first modular-monolith architecture. Cross-feature calls use contract packages only; no cross-feature collection access.
- MongoDB transactions protect a single accepted usage operation. The application/test MongoDB environment must support replica-set transactions.

### Not in scope

- Payment execution or purchase workflows beyond the current Subscription API surface.
- Measuring audio, deciding which seconds count as Speaking usage, or deriving usage from session data.
- Resource ownership/durable access rules for Topic Learning or Assessment.
- GRANT/bonus quota, rollover, manual reset UI, distributed messaging, or microservice extraction.
- A retention/TTL policy for ledger history; decide separately before enabling automatic deletion.

## 2. Vocabulary and initial quota catalog

| Field | Meaning |
|---|---|
| `quotaType` | Stable product-neutral identifier for a type of measured usage |
| `unit` | Canonical unit for this quota type; the same type uses the same unit in every policy scope |
| `period` | Calendar period: `DAILY`, `WEEKLY`, or `MONTHLY` |
| `policyScope` | Scope key selected by Subscription for the user's applicable quota policy, e.g. `FREE`, `TRIAL`, `PREMIUM` |
| `policyVersion` / `policySnapshotId` | Identifies the rules effective for a scope or a user's subscription snapshot |
| `allowanceId` | Identifies the current usage allowance. Changes at a calendar boundary or a distinct quota-policy activation reset; does **not** change merely because a user's tier changes |
| `usedAmount` | Usage accumulated in the current allowance |
| `remaining` | `max(0, limit - usedAmount)`; never return a negative remaining amount |

Initial catalog based on the business rules currently known:

| `quotaType` | UI display name | Canonical `unit` | `period` | Notes |
|---|---|---|---|---|
| `SPEAKING_DURATION` | Thời lượng luyện nói với AI | `SECOND` | `DAILY` | The Speaking feature computes the final amount. A 60-minute Free limit is policy data (3,600 seconds), not a hard-coded engine rule. |
| `PROFICIENCY_CHECK_ATTEMPT` | Lượt làm bài đánh giá trình độ | `ATTEMPT` | `MONTHLY` | Count a successful attempt start according to the Proficiency feature's business rule. The 21-day cooldown remains owned by Proficiency, not Quota. |

These are the currently supported catalog entries, not a closed enum of every future business feature. Adding an ordinary count-based quota should require policy data and caller integration, not a new branch in the Quota engine. Validate that a `quotaType` always uses the same `unit` across policy scopes.

All calendar boundaries use `Asia/Ho_Chi_Minh` and fixed windows:
- `DAILY`: 00:00 to the next local day at 00:00.
- `WEEKLY`: Monday 00:00 to the following Monday at 00:00.
- `MONTHLY`: first day of the month at 00:00 to the first day of the next month at 00:00.

Represent persisted instants as UTC instants; derive the boundaries using the configured business timezone. Windows are half-open: `[start, end)`. Subscription policy activation can start a new allowance *inside* a calendar window; after that reset, the next calendar boundary still starts another allowance.

## 3. Internal contract

Expose a small in-process API from `fit.iuh.engolearn.quota.contract`. Exact Java names can follow existing project style; the behavior is the important contract.

### 3.1 `consume(command)` — known amount, atomic check + record

Use when the caller knows the amount before allowing the action (e.g. one assessment attempt).

Command fields:
- `userId`
- `quotaType`
- `amount` (positive integer in that quota type's canonical unit)
- `sourceFeature`
- `idempotencyKey`
- optional `sourceReference` and `occurredAt` where relevant

Behavior:
1. Resolve the user's current entitlement/policy snapshot and current allowance.
2. Atomically check `usedAmount + amount <= limit`.
3. If permitted, update the counter and append one ledger entry in the same MongoDB transaction.
4. If not permitted, return a business-level quota-exhausted result and do not increase usage or append an accepted-usage ledger entry.
5. Repeating the same accepted request with the same key and same payload returns the original outcome and does not consume again. Reusing the key with a different payload is an idempotency conflict.

The concurrency-safe check must be an atomic conditional update (with transaction/write-conflict retry as needed), not only a read followed by an unconditional increment.

### 3.2 `checkAvailability(userId, quotaType)` — read-only, no reservation

Use when the amount is not known yet, such as before a Speaking recording turn.

- Returns the current policy limit, recorded usage, non-negative remaining amount, unit, and current window end.
- Does not mutate the counter or ledger, and does not reserve quota.
- The result is a snapshot and may become stale before the caller records usage.
- This operation checks **quota availability only**. The owning feature still checks feature-specific preconditions/permissions.

### 3.3 `recordUsage(command)` — record actual amount after the action

Use when the caller determines the actual amount only after its action (e.g. the final Speaking duration).

Command fields:
- `userId`, `quotaType`, positive `amount`, `unit` implied by the type
- `sourceFeature`, stable `idempotencyKey` (or session + segment key), optional `sourceReference`
- an occurrence/window reference sufficient to associate usage with the correct calendar window

Behavior:
1. Validate the command shape and resolve the applicable policy/allowance.
2. Atomically increment the counter and append one ledger entry.
3. Do **not** reject a truthful final usage report merely because it takes the counter above the limit. The action has already occurred; record the actual amount and report the over-limit state. Subsequent availability checks return unavailable until the next allowance.
4. Deduplicate retries by the idempotency key; a duplicate with a different amount is a conflict rather than a second increment.

Quota does not define a permitted Speaking overrun (such as up to one minute). The Speaking feature owns its measuring/cutoff behavior; Quota records the amount provided and exposes the resulting count.

**Speaking integration remains open:** the business rule assigns a session crossing midnight to each day's actual usage. If Speaking submits usage after the session ends, it must provide correctly allocated per-window segments (each with a stable distinct idempotency key) or another agreed time-allocation payload. Quota will not inspect audio or infer the allocation itself.

## 4. Ownership and policy events

Policy configuration is owned by Subscription. Quota maintains a local read projection only; it never reads Subscription collections directly.

Both event types are declared in `subscription.contract`, following the architecture rule that event types belong to the publisher.

### `EntitlementChanged`

Minimum data: `userId`, monotonic `entitlementVersion`, effective `policyScope`, applicable `policySnapshotId`, and `effectiveAt`.

- Refresh the user's quota entitlement projection.
- Do not reset or clear usage.
- Do not key counters by `policyScope` or `policyVersion`: a tier change must preserve already-recorded usage in the same calendar window.
- Ignore stale/out-of-order versions.

This event only describes quota-related entitlement/policy selection. It does not convey lifetime content ownership or Assessment resource access.

### `QuotaPolicyActivated`

Minimum data: stable `activationId`, `policyScope`, activated `policySnapshotId` / `policyVersion`, the changed `quotaTypes`, and `effectiveAt`.

Emit/process it when a policy actually becomes effective, not merely when Admin saves a future-effective configuration.

- Start a new allowance only for the affected scope and quota types whose effective rule changed, whether the limit increased or decreased.
- Retain all prior usage ledger entries.
- Apply each activation idempotently. Reprocessing an already-completed activation must not reset usage a second time.
- Do not reset allowances for unrelated scopes or quota types.
- A tier change alone is not a policy activation and does not reset usage.

The exact event-processing code must keep the reset marker durable and recoverable. Section 7 and the ADR describe the MVP delivery/reconciliation choice.

## 5. MongoDB model proposal

The collections below are logical Quota-owned storage. Policy snapshots and user entitlement are sourced from Subscription; Quota stores projections required for local evaluation.

### 5.1 `quota_policy_snapshots` — immutable policy projection

One document per scope/version snapshot.

```json
{
  "policySnapshotId": "free-v3",
  "policyScope": "FREE",
  "policyVersion": 3,
  "effectiveAt": "2026-11-01T17:00:00Z",
  "timezone": "Asia/Ho_Chi_Minh",
  "rules": [
    {
      "quotaType": "SPEAKING_DURATION",
      "unit": "SECOND",
      "period": "DAILY",
      "limit": 3600
    }
  ]
}
```

Illustrative values only. Snapshots are immutable; policy changes create a new version. Validate non-negative integer limits, supported periods, unique quota types within a snapshot, and canonical unit consistency across scopes. A snapshot/version is not the same as a per-user allowance.

### 5.2 `quota_entitlement_projection` — current quota policy for a user

One document per user:
- `userId` (unique)
- `policyScope`
- `policySnapshotId`
- `entitlementVersion`
- `effectiveAt`
- `updatedAt`

Only data needed to select the applicable quota policy belongs here. It is not a generalized entitlement/resource-permission store.

### 5.3 `quota_usage_counters` — current aggregate

One current document per `(userId, quotaType)`; create lazily on first recorded usage and retain it so a policy reset can distinguish a new allowance from a tier change.

Suggested fields:
- `userId`, `quotaType`, `usedAmount`, `unit`
- `period`, `calendarWindowStartAt`, `calendarWindowEndAt`
- `allowanceStartAt`, `allowanceId`
- `lastAppliedPolicyActivationId` (or an equivalent durable reset marker)
- `revision`, `updatedAt`

The counter is not keyed by tier. An entitlement change updates the projection but leaves usage and `allowanceId` intact. At a calendar boundary, the first operation in the new window initializes a new allowance. At a policy activation reset, the affected active counters get a new `allowanceId` and `usedAmount = 0`; the ledger keeps the old details. A user without a counter has no prior recorded usage for that quota and can still be handled lazily.

### 5.4 `quota_usage_ledger` — append-only detailed history

Append one record for each successfully accepted `consume` and each `recordUsage` result. A read-only check and a rejected `consume` do not create a usage record.

Suggested fields:
- `ledgerId`, `userId`, `quotaType`, `amount`, `unit`, operation kind
- `sourceFeature`, `sourceReference`, `idempotencyKey`
- `policyScope`, `policySnapshotId`, `policyVersion`
- `allowanceId`, calendar window start/end
- `occurredAt`, `recordedAt`, `usedAfter`, `limitAtRecord`

The ledger preserves the policy and allowance context under which the usage was recorded. Policy/calendar resets never delete or rewrite old ledger entries. A future retention policy must be explicit; do not add a TTL index in the MVP.

### 5.5 `quota_policy_activation_receipts` — idempotency and recovery

Track each activation and affected quota type with `activationId`, `policyScope`, `policyVersion`, `quotaType`, `effectiveAt`, processing state, and completion timestamp. Unique key: `(activationId, quotaType)`.

This is a processing receipt, not an outbox. It lets Quota recognize already-applied resets and resume incomplete reset processing after restart. Reconciliation must process activations in effective/version order for a scope.

### Indexes

| Collection | Index | Purpose |
|---|---|---|
| `quota_policy_snapshots` | Unique `(policyScope, policyVersion)`; unique `policySnapshotId` | One immutable version per scope |
| `quota_entitlement_projection` | Unique `userId` | One effective quota entitlement per user |
| `quota_usage_counters` | Unique `(userId, quotaType)` | One current aggregate per user/type |
| `quota_usage_ledger` | Unique `(userId, sourceFeature, idempotencyKey)`; query index `(userId, quotaType, recordedAt)` | Idempotency and audit/history lookup |
| `quota_policy_activation_receipts` | Unique `(activationId, quotaType)` | Idempotent reset and replay |

All updates to one usage operation's counter and ledger must occur in one MongoDB transaction. Indexes are required to make retry and concurrency behavior safe, not just faster.

## 6. User-facing REST API boundary

These are target read contracts for the UI, separate from `quota.contract`. These proposed paths follow the repo's common `/api/v1` convention. Before implementation, reuse an existing endpoint if the repository already has the same capability; do not create duplicates just to match these proposed paths.

### `GET /api/v1/subscription/plans`

For the plan/package information page. Return the plans currently published for selection and display-ready benefit metadata; prices and benefits come from Subscription configuration, not FE hardcoding.

### `GET /api/v1/subscription/me`

For current plan/trial state and the plan page's eligible CTA. Return current tier/status, applicable period start/end when present, and trial eligibility if available. Do not include a generic `hasLifetimeContentAccess` flag; that policy belongs to Topic Learning.

### `GET /api/v1/quota/me/usage`

For F2's single usage overview request. Return one item per quota currently applicable to the signed-in user, with:
- `quotaType`, localized/display name, `used`, `limit`, non-negative `remaining`, `unit`, `period`
- current `windowEndsAt` and a UI status such as `AVAILABLE`, `EXHAUSTED`, or `OVER_LIMIT`

Actual usage may exceed the limit for post-action recording; in that case return the actual `used`, `remaining = 0`, and `OVER_LIMIT`. Never fabricate zero usage when it could not be read. For MVP prefer a clear unavailable error for the whole summary if required usage data cannot be confirmed, so FE can show an explicit unavailable state rather than false figures.

Follow the existing backend response envelope (`ApiResponse`) and error pattern (`ProblemDetail`). Do not expose raw counter/ledger documents. No public endpoint lets FE call `consume` or `recordUsage`; those are in-process module contracts.

## 7. Consistency, reset, and recovery

### Usage path

- A successful `consume` or `recordUsage` writes counter and ledger in one MongoDB transaction.
- Use an atomic conditional counter update for `consume`; handle concurrent create/update conflicts with bounded retry. If the transaction fails, neither counter nor ledger is committed.
- `recordUsage` always records a validated actual amount, even if the aggregate now exceeds the configured limit. Its result can indicate `OVER_LIMIT`; the next `checkAvailability` denies new work.
- Counter reads with no document are interpreted as zero only when the policy/projection lookup itself succeeded. A failed lookup is not a zero.
- The per-request result is based on the policy snapshot effective at that action; capture that snapshot/version in ledger to make audits interpretable.

### Calendar and policy reset

- Calendar changes reset the current aggregate lazily on the first request in the new window. No midnight/monthly reset job is needed for ordinary window boundaries.
- A policy activation is a separate reset cause, even when the calendar period has not ended.
- The activation processor updates only existing current counters for users whose projected scope and quota type are affected; users without a counter are handled lazily. It writes a receipt per activation/type and must be replayable/idempotent.
- An entitlement/tier change never resets a counter. The applicable limit may change; if existing usage is equal to or above the new limit, future `consume` calls are rejected and availability returns zero remaining.
- Policy activation and reset must be ordered per scope; a stale activation must never overwrite a newer snapshot/reset.

### Event delivery without Outbox

MVP uses in-process events after the Subscription transaction commits. It does not promise durable in-process delivery. Subscription remains the source of truth for current entitlement/policy snapshots. Quota must reconcile its projections and activation receipts from that durable source on startup before serving quota decisions; if an activation handler fails while the application is running, the affected scope must be reconciled/retried before it can use stale policy state.

This is deliberately a limited same-application MVP choice, detailed in the separate ADR. It does not relax the transaction requirement for usage counter + ledger writes.

## 8. Verification checklist for implementation

- [ ] `consume`: allowed amount increments once; over-limit attempt has no usage increment.
- [ ] Concurrent `consume` requests cannot exceed the limit.
- [ ] Retry with same key/same payload does not duplicate usage; same key/different payload is rejected.
- [ ] `checkAvailability` has no mutation and makes no reservation.
- [ ] `recordUsage` records actual usage above the cap and the next availability check blocks.
- [ ] Counter and ledger rollback together when a transaction fails.
- [ ] Free → Premium and Premium → Free changes scope without resetting current-window usage.
- [ ] Policy activation resets only the selected scope/types, including a lower limit; repeated/replayed activation does not reset twice.
- [ ] Usage ledger remains intact across calendar/policy resets.
- [ ] Daily, Monday-based weekly, and calendar-month windows use `Asia/Ho_Chi_Minh`.
- [ ] Requests with missing/stale policy projection recover before quota is evaluated.
- [ ] REST usage summary returns all current quota items and does not turn unavailable data into zero.
- [ ] Transaction/concurrency tests use a MongoDB replica-set test environment.

## 9. Open items for design review (not assumptions for an agent to invent)

1. **Speaking allocation payload:** confirm how Speaking reports actual usage for a session crossing midnight; the owning feature must supply the amount and time allocation.
2. **Event/reconciliation contract:** confirm the smallest Subscription contract capability that returns authoritative current snapshots/entitlements and lets Quota reconcile missed in-process events.
3. **REST path/shape review:** compare the target read APIs above with any existing controllers before FE freezes its handoff. The suggested paths are not a claim that these endpoints already exist.
4. **Ledger retention:** no TTL/automatic deletion until a retention requirement is agreed.
