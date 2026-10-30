# Quota Engine — Technical Design (Draft)

> Status: **Implementation baseline — MVP decisions recorded**. The core Quota behavior and event-delivery trade-off below are agreed. REST path/shape validation against existing controllers and ledger retention remain review items; they do not block implementation of the Quota core.
>
> Scope: Subscription/Quota backend contracts, MongoDB usage data, event handling, consistency, and the narrow REST read APIs needed by the package/usage UI.

## Business-rule sources

Treat the following business documents as the baseline for the policy behaviors in this design:

- [Subscription module baseline and ownership boundaries](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/feat/subscription-quota-policy-reset/docs/usecase_spec/subscription/00-module.md)
- [F2 — Usage-limit overview rules](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/feat/subscription-quota-policy-reset/docs/usecase_spec/subscription/f2-usage-limits/rules.md)
- [F4 — Quota configuration rules](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/feat/subscription-quota-policy-reset/docs/usecase_spec/subscription/f4-quota-management/rules.md)
- [Speaking session rules](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/feat/subscription-quota-policy-reset/docs/usecase_spec/speaking-with-AI/f01-speaking-session/rules.md)
- [Proficiency assessment rules](https://github.com/engolearn-platform/engolearn-business-docs-and-tools/blob/feat/subscription-quota-policy-reset/docs/usecase_spec/proficiency-check/f01-lam-bai-danh-gia/rules.md)

These references point to the current business-document working branch for review; they are not a claim that the branch has been merged.

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
| `unit` | Canonical unit for this quota type; the same type uses the same unit in every tier/scope and version |
| `period` | Calendar period: `DAILY`, `WEEKLY`, or `MONTHLY`; fixed per quota type across tiers/scopes and versions in MVP |
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

These are the currently supported catalog entries, not a closed enum of every future business feature. Adding an ordinary count-based quota should require policy data and caller integration, not a new branch in the Quota engine. In MVP, a `quotaType` must always use the same `unit` **and** `period` across all tiers/scopes and versions. Reject configurations that violate either invariant; limits may differ.

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

**Proficiency start transaction:** Proficiency owns one MongoDB transaction covering attempt creation, timer start, `consume` counter update, and ledger append. `quota.contract` joins the caller's transaction and must not commit independently. Quota denial or any failure rolls back all four effects; no official attempt content is released before commit. A retry of the same successful start returns the existing attempt without starting another timer or consuming again. Perform external/resource preparation before opening the transaction, then recheck mutable start preconditions inside it before accepting the start.

### 3.2 `checkAvailability(userId, quotaType)` — read-only, no reservation

Use when the amount is not known yet, such as before a Speaking recording turn.

- Returns the current policy limit, recorded usage, non-negative remaining amount, unit, and current window end.
- Does not mutate usage counters or the ledger, and does not reserve quota. A conditional entitlement-projection refresh is allowed as described in section 4.
- The result is a snapshot and may become stale before the caller records usage.
- This operation checks **quota availability only**. The owning feature still checks feature-specific preconditions/permissions.

### 3.3 `recordUsage(command)` — record actual amount after the action

Use when the caller determines the actual amount only after its action (e.g. the final Speaking duration).

Command fields:
- `userId`, `quotaType`, positive `amount`, `unit` implied by the type
- `sourceFeature`, stable `idempotencyKey` (Speaking: one stable key per audio turn), optional `sourceReference`
- optional `occurredAt` for audit context; it does not select the quota window

Behavior:
1. Validate the command shape and resolve the applicable policy plus the calendar window containing Quota's server-side `recordedAt` at the first successful acceptance. `occurredAt`, if supplied, is audit metadata only and does not backdate usage into an earlier window.
2. Atomically increment the counter and append one ledger entry.
3. Do **not** reject a truthful final usage report merely because it takes the counter above the limit. The action has already occurred; record the actual amount and report the over-limit state. Subsequent availability checks return unavailable until the next allowance.
4. Deduplicate retries by the idempotency key; a duplicate with a different amount is a conflict rather than a second increment. Once accepted, a retry after a calendar boundary returns the original outcome and never moves the ledger entry to another window.

Quota does not define a permitted Speaking overrun (such as up to one minute). The Speaking feature owns its measuring/cutoff behavior; Quota records the amount provided and exposes the resulting count.

**Speaking recording unit and midnight rule (decided):** Speaking calls `recordUsage` for each audio turn with that turn's stable idempotency key; do not accumulate usage until session end. The whole measured turn amount belongs to the calendar window containing the first accepted server-side `recordedAt`, using `Asia/Ho_Chi_Minh`. For example, if a turn starts at 23:59 but its `recordUsage()` is first accepted at 00:01, the whole turn is charged to the new day. Do not split a turn across days. Retries retain the original window and result. `occurredAt` is audit metadata, not the window selector. This changes recording granularity/window assignment only; Speaking's existing definition of measured usage remains unchanged.

## 4. Ownership and policy events

Policy configuration is owned by Subscription. Quota maintains a local read projection only; it never reads Subscription collections directly.

Quota depends on `subscription.contract` for an effective-entitlement lookup at the current server time. If the local projection is absent, initialize it from this contract; never assume Free. Time-limited entitlement results and events include `validUntil`. When `now >= validUntil`, resolve effective entitlement through the contract even if a status-update job has not run; Subscription resolves by effective times, not just stored lifecycle status. Conditional lookup may refresh the projection, including during `checkAvailability`, but availability checks never write usage counters or ledger. A failed lookup returns unavailable rather than a fabricated policy. This is on-demand entitlement resolution, not missed-event replay/reconciliation. Subscription publishes its own events and never calls Quota to resolve entitlement; the dependency direction is Quota -> Subscription contract, avoiding a cycle.

Both event types are declared in `subscription.contract`, following the architecture rule that event types belong to the publisher.

### `EntitlementChanged`

Minimum data: `userId`, monotonic `entitlementVersion`, effective `policyScope`, applicable `policySnapshotId`, `effectiveAt`, and `validUntil` for time-limited entitlements.

- Refresh the user's quota entitlement projection.
- Do not reset or clear usage.
- Do not key counters by `policyScope` or `policyVersion`: a tier change must preserve already-recorded usage in the same calendar window.
- Ignore stale/out-of-order versions.

This event only describes quota-related entitlement/policy selection. It does not convey lifetime content ownership or Assessment resource access.

### `QuotaPolicyActivated`

Minimum data: stable `activationId`, `policyScope`, activated `policySnapshotId` / `policyVersion`, the changed `quotaTypes`, and `effectiveAt`.

Emit/process it when a policy actually becomes effective, not merely when Admin saves a future-effective configuration.

- Publishing a new Premium/Trial version affects only new periods; existing snapshot holders retain their snapshot and allowance. Starting a new period or changing tier preserves current-window usage.
- For a scheduled Free-policy activation, Subscription publishes this event after the activation transaction commits. Start a new allowance only for currently Free users and the quota types whose effective rule changed, whether the limit increased or decreased.
- Retain all prior usage ledger entries.
- Apply each activation idempotently. Reprocessing an already-completed activation must not reset usage a second time.
- Scope membership alone is insufficient to reset old snapshot holders. Do not reset existing Premium/Trial snapshots or unrelated quota types.
- A tier change alone is not a policy activation and does not reset usage.

The activation receipt is durable for duplicate detection, but the MVP does not guarantee replay or recovery for an event that was never applied. Section 7 and the ADR describe this accepted limitation.

## 5. MongoDB model proposal

The collections below are logical Quota-owned storage. Policy snapshots and user entitlement are sourced from Subscription; Quota stores projections required for local evaluation. **MVP decision: retain all five collections below as separate responsibilities; they are part of the initial implementation, not an optional future-state model.**

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

Illustrative values only. Snapshots are immutable; policy changes create a new version. Validate non-negative integer limits, supported periods, unique quota types within a snapshot, and canonical unit and period consistency across tiers/scopes and versions. Reject inconsistent configurations. A snapshot/version is not the same as a per-user allowance.

### 5.2 `quota_entitlement_projection` — current quota policy for a user

One document per user:
- `userId` (unique)
- `policyScope`
- `policySnapshotId`
- `entitlementVersion`
- `effectiveAt`
- `validUntil` for time-limited entitlement; absent only for entitlement without a time limit
- `updatedAt`

Only data needed to select the applicable quota policy belongs here. Initialize an absent projection and resolve an expired one through `subscription.contract` as specified in section 4. It is not a generalized entitlement/resource-permission store.

### 5.3 `quota_usage_counters` — current aggregate

One current document per `(userId, quotaType)`; create lazily on first recorded usage and retain it so a policy reset can distinguish a new allowance from a tier change.

Suggested fields:
- `userId`, `quotaType`, `usedAmount`, `unit`
- `period`, `calendarWindowStartAt`, `calendarWindowEndAt`
- `allowanceStartAt`, `allowanceId`
- `lastAppliedPolicyActivationId` (or an equivalent durable reset marker)
- `revision`, `updatedAt`

The counter is not keyed by tier. An entitlement change or new period updates the projection but leaves current-window usage and `allowanceId` intact. At a calendar boundary, the first usage write in the new window initializes a new allowance; reads only compute the current-window view. At a qualifying Free-policy activation reset, affected counters get a new `allowanceId` and `usedAmount = 0`; the ledger keeps the old details. A user without a counter has no prior recorded usage for that quota and can still be handled lazily.

### 5.4 `quota_usage_ledger` — append-only detailed history

Append one record for each successfully accepted `consume` and each `recordUsage` result. A read-only check and a rejected `consume` do not create a usage record.

Suggested fields:
- `ledgerId`, `userId`, `quotaType`, `amount`, `unit`, operation kind
- `sourceFeature`, `sourceReference`, `idempotencyKey`
- `policyScope`, `policySnapshotId`, `policyVersion`
- `allowanceId`, calendar window start/end
- `occurredAt`, `recordedAt`, `usedAfter`, `limitAtRecord`

The ledger preserves the policy and allowance context under which the usage was recorded. Policy/calendar resets never delete or rewrite old ledger entries. A future retention policy must be explicit; do not add a TTL index in the MVP.

### 5.5 `quota_policy_activation_receipts` — activation idempotency

Track each successfully applied activation and affected quota type with `activationId`, `policyScope`, `policyVersion`, `quotaType`, `effectiveAt`, processing state, and completion timestamp. Unique key: `(activationId, quotaType)`.

This is a processing receipt, not an outbox, queue, or recovery log. It makes repeated delivery a no-op; it cannot recover an event that Quota never applied. For MVP, apply all affected quota types and write their receipts in one MongoDB transaction per activation, so a failed transaction cannot leave a partially applied activation marked complete. No startup replay/reconciliation is implemented.

### Indexes

| Collection | Index | Purpose |
|---|---|---|
| `quota_policy_snapshots` | Unique `(policyScope, policyVersion)`; unique `policySnapshotId` | One immutable version per scope |
| `quota_entitlement_projection` | Unique `userId` | One effective quota entitlement per user |
| `quota_usage_counters` | Unique `(userId, quotaType)` | One current aggregate per user/type |
| `quota_usage_ledger` | Unique `(userId, sourceFeature, idempotencyKey)`; query index `(userId, quotaType, recordedAt)` | Idempotency and audit/history lookup |
| `quota_policy_activation_receipts` | Unique `(activationId, quotaType)` | Idempotent event handling |

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

## 7. Consistency, reset, and event-delivery risk

### Usage path

- A successful `consume` or `recordUsage` writes counter and ledger in one MongoDB transaction.
- Use an atomic conditional counter update for `consume`; handle concurrent create/update conflicts with bounded retry. If the transaction fails, neither counter nor ledger is committed.
- `recordUsage` always records a validated actual amount, even if the aggregate now exceeds the configured limit. Its result can indicate `OVER_LIMIT`; the next `checkAvailability` denies new work.
- Counter reads with no document are interpreted as zero only when the policy/projection lookup itself succeeded. A failed lookup is not a zero.
- The per-request result is based on the policy snapshot effective at that action; capture that snapshot/version in ledger to make audits interpretable.

### Calendar and policy reset

- Calendar changes initialize the current aggregate lazily on the first usage write in the new window. Availability reads report the current-window view without mutating counters/ledger. No midnight/monthly reset job is needed for ordinary window boundaries.
- A Free-policy activation is a separate reset cause, even when the calendar period has not ended. Premium/Trial publication does not reset existing snapshot holders.
- The activation processor updates only existing current counters for currently Free users and changed quota types whose effective rule is replaced by that activation; scope alone must not select old snapshot holders for reset. Users without a counter are handled lazily. Apply all affected types and write their activation receipts in one MongoDB transaction per activation. A repeated event is idempotent; the receipt does not provide replay for an event that was never applied.
- An entitlement/tier change or new Subscription period never resets a counter. The applicable limit may change; if existing usage is equal to or above the new limit, future `consume` calls are rejected and availability returns zero remaining.
- Policy activation and reset must be ordered per scope; a stale activation must never overwrite a newer snapshot/reset.

### Event delivery without Outbox

MVP uses in-process events after the Subscription transaction commits, including scheduled Free activation. It does not promise durable delivery, retry, or recovery of events that were not applied. If the process fails after Subscription commits but before Quota commits the event-handler transaction, an `EntitlementChanged` projection update or `QuotaPolicyActivated` reset can be missed. This risk is explicitly accepted for MVP. No Outbox, startup replay, or reconciliation is implemented, and Quota must not read Subscription collections directly. Conditional entitlement lookup on missing/expired projections is normal request-time resolution, not event recovery. In particular, a missed early refund event can leave an unexpired projection stale until its `validUntil`; a missed activation can leave an allowance unreset. Both risks remain accepted. Activation receipts guard duplicate handling and cannot detect an unapplied event.

This is a deliberately limited same-application MVP choice, detailed in the separate ADR. It does not relax the transaction requirement for usage counter + ledger writes.

## 8. Verification checklist for implementation

- [ ] `consume`: allowed amount increments once; over-limit attempt has no usage increment.
- [ ] Concurrent `consume` requests cannot exceed the limit.
- [ ] Retry with same key/same payload does not duplicate usage; same key/different payload is rejected.
- [ ] `checkAvailability` never mutates usage counters/ledger or reserves quota; conditional projection refresh is allowed.
- [ ] `recordUsage` records actual usage above the cap and the next availability check blocks.
- [ ] Counter and ledger rollback together when a transaction fails.
- [ ] Proficiency attempt, timer, quota counter and ledger commit/rollback in one caller-owned transaction; denial/failure leaves none committed; retry returns the existing attempt without double counting. External preparation precedes the transaction and mutable preconditions are rechecked inside it.
- [ ] Free → Premium and Premium → Free changes scope without resetting current-window usage.
- [ ] New Premium/Trial versions and new periods preserve existing current-window usage/snapshots as applicable; scope alone never resets old snapshot holders.
- [ ] Scheduled Free activation publishes after commit and resets only changed types for currently Free users, including a lower limit; duplicate delivery does not reset twice.
- [ ] Reject the same quota type with different units or periods across tiers/scopes or versions.
- [ ] Speaking records per audio turn with a stable turn key; a midnight-spanning turn is charged wholly at first accepted `recordedAt`, and later retries never move or duplicate it.
- [ ] Usage ledger remains intact across calendar/policy resets.
- [ ] Daily, Monday-based weekly, and calendar-month windows use `Asia/Ho_Chi_Minh`.
- [ ] Missing projection uses the effective-entitlement contract, including active Premium/Trial; never assume Free. Expired `validUntil` triggers time-based contract resolution even with a delayed status job. Lookup failure returns unavailable.
- [ ] Conditional lookup introduces no Subscription -> Quota dependency or event replay/reconciliation; missed early refund and missed reset remain documented accepted risks.
- [ ] REST usage summary returns all current quota items and does not turn unavailable data into zero.
- [ ] Transaction/concurrency tests use a MongoDB replica-set test environment.

## 9. Remaining review items (not blockers for the Quota core)

1. **REST path/shape review:** compare the target read APIs above with existing controllers before FE freezes its handoff. The suggested paths are not a claim that these endpoints already exist; reuse an existing endpoint when it provides the same capability.
2. **Ledger retention:** no TTL/automatic deletion until a retention requirement is agreed.

## 10. Admin immutable configuration bundles (M5)

The ADMIN-only `/api/v1/subscription/admin` interface reads a consistent Mongo transaction
baseline, previews without publishing, and appends a complete scope bundle. The HTTP input
contains authored metadata, benefits, billing options and explicit quota rules, never operator
manifests, account histories or storage snapshot IDs. The OpenAPI defines all six operations.

One transaction inserts the policy and its `CurrentBenefits`, the matching FREE/PREMIUM plan,
the actor/operation/key receipt and the next GLOBAL configuration revision. TRIAL has only a
policy/benefits bundle and uses PREMIUM as its benefit plan code. Descriptions and benefit
display values retain their nullable/empty meaning. Reads reject missing required FREE/TRIAL
baselines or mismatched plan/policy display benefits; Admin does not migrate existing data.

Every publication writer, including the external operator and activation scheduler, writes
the GLOBAL revision inside its transaction. This serializes historical canonical unit/period
validation across scopes, as well as invalidating stale editors. Scope/version and receipt
identity indexes are created before publication transactions. Initializing the GLOBAL token
does not rewrite plans, policies, accounts or snapshots and is not an eager startup action.

An accepted same-actor/key/normalized-input receipt is checked before revision, pending and
time preconditions and returns the original result. The fingerprint includes scope, expected
revision and effective-time intent; NOW's accepted time is not fingerprint input. Transient
Admin transaction retries are bounded to three attempts. Unknown commit results are resolved
by reading the receipt; absent/unreadable receipts return CONFIGURATION_UNAVAILABLE, preserving
the client's exact intent and key. A changed same-key body is an IDEMPOTENCY_CONFLICT.

FREE always activates at the next Vietnam midnight. Admin NOW for TRIAL/PREMIUM makes the new
bundle eligible for new periods immediately; AT schedules it for a future instant. The operator
manifest keeps its existing future-only update semantics. Existing period snapshots remain
immutable, and TRIAL/PREMIUM changes emit no reset. A due pending policy is CURRENT by server
time even before the worker moves its pointer; activationPending reports that lag separately.
Any pending pointer still blocks another publication. FREE retains the existing selective
after-commit activation event and ADR-0001 delivery limitations.

The UI has only unsaved local form state. Saved versions cannot be edited, deleted or canceled.
Quota removal, manual resets/bonuses, account provisioning, checkout and paid lifecycle handlers
are outside this interface. Payment success/refund records are declarations only; see the
[M3 handoff](payment-contract-handoff.md).
