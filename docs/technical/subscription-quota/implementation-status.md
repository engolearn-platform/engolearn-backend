# Subscription and Quota implementation status

Updated: 2026-10-29. Scope: M1 + M2 + M4-contract. This replaces the initial missing-capability survey.

## Current outcome

Subscription and Quota source implementations, learner read APIs, one-time Trial activation and test-only caller contracts are present. Live local API reads and browser Trial activation have been observed against a separate real replica-set database. This is not production readiness: full backend verification failed in the existing Topic area and the unavailable-database context test; actual Speaking/Proficiency, Payment and Admin remain deferred.

Business documentation remains the policy authority. [Technical design](quota-engine-design.md), [OpenAPI](quota-api.openapi.yaml) and [ADR-0001](../../architecture/decisions/ADR-0001-no-outbox-for-quota-mvp.md) define the implementation baseline. OpenAPI now carries IMPLEMENTATION_CONTRACT, not the former draft-read-only status.

## Capability ledger

Source paths below are backend-relative. Present means implemented source; execution claims are listed separately.

| Capability | Current implementation | Limits / next work |
| --- | --- | --- |
| Published offers | subscription/controller/SubscriptionController and subscription/service/SubscriptionService under src/main/java/fit/iuh/engolearn | Effective versioned plans; no invented production pricing |
| Current account summary | Same controller/service; immutable currentBenefits separate from public offers | Trial eligibility requires explicit persisted history; missing history/config is unavailable |
| Effective entitlement | subscription/contract/EffectiveEntitlementProvider and contract DTOs | Time-based resolution, validUntil and composite user/policy freshness; no resource-level lifetime-access flag |
| Trial | POST /api/v1/subscription/me/trial, SubscriptionService, account/period/receipt records | Single Mongo transaction, lifetime partial unique index, UUID idempotency; no paid activation |
| Trial precision | Accepted start normalized to Mongo milliseconds before deriving period and response | Regression added for exact original/persisted replay equality with a high-resolution clock |
| Quota capabilities | quota/contract/QuotaConsumption, QuotaUsageRecording, QuotaAvailability | Real in-process interfaces implemented by quota/service/QuotaService; no public usage-write API |
| Usage persistence | quota/repository/QuotaStore and quota/service/QuotaTransactions | Five collection responsibilities, unique indexes, bounded transaction conflicts, counter+ledger atomicity, caller-owned transaction joining |
| Policy delivery | quota/service/QuotaEntitlements and QuotaActivationHandler | Ordered/idempotent delivered activation, affected Free quota types only, retained history/post-effective usage; no missed-event recovery |
| User overview | quota/controller/QuotaController | Actual recorded counts, per-item calendar/allowance boundaries; complete summary or unavailable; no failed lookup converted to zero |
| Authentication/errors | config/SecurityConfig plus feature advice | Public plans; authenticated self reads/Trial; no-store and ProblemDetail codes. Dev fixture headers are local-only, not production sign-in |
| Operator configuration | config/SubscriptionOperatorConfig, tools/subscription/README.md | Explicit external versioned manifest and account-history provenance; no Admin UI, no direct counter imports |
| Runtime | tools/subscription/compose.replica.yaml and application-subscription-local.yaml | Opt-in replica set on loopback27018, separate volume/database, transaction manager enabled; default standalone runtime unchanged |
| Test-only producers | src/test/java/fit/iuh/engolearn/quota/QuotaIntegrationTest.java | Fake owning-feature callers use real Quota and isolated replica Mongo; no actual audio/assessment module |

For shortened feature paths, prefix src/main/java/fit/iuh/engolearn/. Architecture roots/dependency table were updated for subscription and quota only. Quota depends on subscription.contract; Subscription publishes events without a reverse Quota dependency.

## HTTP contract implemented

| Route | Access / behavior |
| --- | --- |
| GET /api/v1/subscription/plans | Public; data.items with configured benefits and billing options |
| GET /api/v1/subscription/me | Authenticated; current tier/status, explicit nullable period dates, eligibility/reason and immutable currentBenefits |
| GET /api/v1/quota/me/usage | Authenticated; data.items with actual used/limit/remaining, extensible identifiers, unit/period, windowStartAt/windowEndsAt/allowanceStartAt and status |
| POST /api/v1/subscription/me/trial | Authenticated; empty JSON object, UUID Idempotency-Key; 201 creation/200 accepted replay; original period values, no extension |

The OpenAPI is the field/status/error authority. Trial is tier PREMIUM with status TRIAL, not a third tier enum. A new tier/period retains same-window usage. A delivered Free policy activation is a separate reset cause. No purchase/reconciliation/refund API or actual producer endpoint is implied.

## Verification observed

- Scoped Quota group: 20 tests passed, zero failures/errors/skips, real replica-set Mongo. Covers bounded concurrent creation/consume, rollback, caller transaction joining, per-turn midnight/replay/overrun, projection freshness and activation ordering/selective reset.
- Subscription integration: five tests passed plus one feature security test. Architecture nine, common security nine and production-header guard three also passed during the combined verification run.
- One full mvn.cmd verify run: 287 tests, four failures, 37 errors, zero skips. Failure areas include unchanged Topic test mocks/retired endpoints/quiz expectations, plus the context smoke test at intentional Mongo port1. This run did NOT pass; no second full run was spent on unrelated failures.
- Quota eager-index startup regression was removed: index initialization is conditional on transaction-manager availability, while enabled transactional runtime still enforces indexes and operations without a manager fail unavailable. One scoped smoke retest then failed in unchanged TopicAttemptService.indexes connecting to intentional port1; that remaining failure was not hidden or skipped.
- New timestamp regression: SubscriptionIntegrationTest#highResolutionClockActivationAndPersistedReplayHaveExactlyEqualResults passed once against real replica Mongo after a live replay precision mismatch was reproduced and fixed. This targeted execution overwrites that suite's latest Surefire XML; do not sum mixed-run XML to reconstruct the earlier full run.
- Live FE proxy: published plans200, confirmed Free summary200, quota200 and anonymous quota401; Cache-Control:no-store observed. Real browser activation changed dev-user to Trial with seven-day displayed dates and assessment limit2. No usage was fabricated; counts remain genuinely unrecorded zero until real producers exist.

- Final live replay retest after the fix passed:201 then200 with identical period/start/end and seven-day duration. Browser guest/identity isolation and inspected mobile390/desktop1024 horizontal fit were also observed.

Detailed commands, budgets and remaining limitations are maintained in the workspace .artifacts/subscription-user-first/{execution-log,test-plan,agent-a,agent-b,agent-c}.md. Maven wrapper failed before tests in this Windows environment; installed Maven3.9.16 was used with explicit existing local cache. Packaging/startup is not substituted for verification.

## Accepted limits and future work

- No Outbox/startup replay/reconciliation. Missed early refund may leave an unexpired projection stale; missed activation may leave allowance unreset. Receipts prevent duplicates, not event loss.
- Unknown-commit driver-error injection and population-scale all-users activation are not verified; actual producer integration is not claimed by test callers. All-users reset transaction capacity needs a separate scale decision before large rollout.
- Product prices/quotas, historic account migration and production sign-in are not approved by local verification. The user explicitly approved temporary isolated Free/Trial Speaking3600 DAILY and assessment Free1/Trial2 MONTHLY, with no Premium price.
- Payment (including Trial-to-paid termination and payment return/reconciliation questions) and Admin remain later scope. Ledger has no TTL/automatic deletion.
- Keep current implementation and verification separate when updating this file. Add source/test/runtime evidence as work lands; never mark real Speaking/Proficiency integration complete from mock-caller tests.
