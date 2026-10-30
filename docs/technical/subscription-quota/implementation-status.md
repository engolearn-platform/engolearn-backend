# Subscription and Quota implementation status

## P04 paid activation (2026-10-30)

Internal Payment outcome operations commit normalized verified facts and result Outbox atomically; future provider adapters must authenticate/verify provider evidence before using them. No HTTP endpoint or fake provider can force success. Subscription result handling validates pinned correlation/amount/currency and atomically supersedes eligible Trial, creates one Premium period, records receipt, updates purchase and emits current effective entitlement. Monthly/yearly duration uses calendar arithmetic in Asia/Ho_Chi_Minh with UTC millisecond storage; late delivery does not extend the interval. Original Trial scheduled end is retained for audit; SUPERSEDED/supersededAt describe its actual termination. Usage is preserved. Contradictions are retained for review without clearing another purchase's lock.

Verification: nine architecture checks passed. Seven P04 cases passed, including twice-run concurrent success, receipt-failure rollback, month-end/leap-year, retained usage, expiry and late facts. A test-framework condition failed before these cases could run through an inherited fixture; the cases were moved into the already-verified isolated fixture and only P04 methods rerun. Earlier P03 passing evidence was reused. Full-project verify was not run under the focused budget. BE read checks: plans 200 and an authenticated missing purchase returned 404 PURCHASE_NOT_FOUND. No dev purchases, payments or usage were fabricated. P05 history list, P06 refund and P07 provider integration remain unimplemented.

## P03 purchase orchestration (2026-10-30)

Create/detail learner purchase APIs, pinned matching plan/policy/price snapshots, request idempotency, account revision serialization and one-blocking-purchase constraints are implemented. Payment intake commits a setup-pending transaction and receipt; actual checkout preparation/provider execution is not automatic. Checkout/definitive-failure/expiry projections use durable result receipts, with conflict review evidence. See [purchase API](purchase-api.md). Paid activation, history list, refund and VNPay remain later slices.

Verification: nine architecture checks passed; the purchase suite ran seven cases (including repeated concurrency) with one HTTP fixture identity error. That fixture was corrected to the real dev-auth account ID; the single HTTP rerun passed. Two test corrections were used (initial fixture compilation, then identity mapping); no application assertion was weakened and no full-project suite was run.

## P01-P02 source update (2026-10-30)

Observed verification: the initial critical batch ran 26 tests with one concurrent Trial error; all nine architecture checks, two selected Quota checks and nine new durable checks passed. After bounded retry waits and test storage setup were corrected, the focused rerun passed all 10 tests (nine durable checks plus the failed Trial case), with no failures/errors/skips. No assertions were weakened. Normal dev BE started on 8080 and public plans returned 200 with two items and no-store. One correction rerun was used; full-project verify was not run under the user's explicit focused-testing budget. Exact logs are in `.artifacts/subscription-payment-p01-p02/` at the workspace root.

Payment command/result records now live in `payment.contract.dto` / `payment.contract.event`, with schema/identity metadata, VND integer validation and refundReference. Purchase/Payment/Refund persistence models and uniqueness indexes are present; no purchase API, provider execution, paid activation or refund orchestration is added.

Durable module-owned Outboxes/receipts and an internal recipient-scoped dispatcher are implemented. Existing Trial and scheduled Free activation enqueue typed snapshots atomically; Quota applies effects with its receipt in a separate transaction. The old in-memory listener is removed. Shared transport is feature-neutral; Payment business handlers remain absent until later slices. Entitlement fingerprints lazily advance existing account versions with revision CAS, preserving policy freshness and account/trial history; public Subscription resolution commits independently of a Quota transaction. No database reset or historical event backfill is performed.

Delivery defaults: 1-second poll, 30-second lease, 5-second exponential retry base capped at 64 times base, 20 messages per outbox per tick; all are configurable under `engolearn.messaging`. These are development defaults, not measured production tuning. No message/receipt TTL is introduced. Future Payment routes are not claimed without a registered consumer. Verification evidence and limits are recorded in workspace `.artifacts/subscription-payment-p01-p02/`; earlier execution results below are historical.

Updated: 2026-10-29. Scope: M1 + M2 + M4-contract, M3 declarations and M5 Admin configuration.

Design update 2026-10-30: the [canonical Subscription/Payment design](subscription-payment-technical-design.md)
and [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md)
record the agreed future durable delivery baseline, Trial supersede at paidAt,
calendar Premium periods, refundReference contracts and safe checkout expiry handling.
These decisions supersede ADR-0001 for future implementation, not the actual runtime
or historic test evidence below. Outbox/receipts delivery, paid lifecycle and provider
integration remain unimplemented. The earlier Trial-conversion question is resolved
in design; payment methods/return UX remain open. P01-P07 are proposed work only.

## Current outcome

Subscription and Quota source implementations, learner read APIs, one-time Trial activation and test-only caller contracts are present. Live local learner API reads and browser Trial activation have been observed against a separate real replica-set database. M5 now has typed ADMIN-only configuration reads, history, preview and atomic append-only bundle publication in source; its new execution evidence is tracked separately below. M3 adds only Payment fact declarations and a compile-only consumer example. Actual Speaking/Proficiency and Payment runtime remain deferred. Previous full backend verification failed in existing Topic expectations and the unavailable-database context; this ledger does not claim whole-project green or production readiness.

Business documentation remains the policy authority. [Quota design](quota-engine-design.md), [OpenAPI](quota-api.openapi.yaml) and historical [ADR-0001](../../architecture/decisions/ADR-0001-no-outbox-for-quota-mvp.md) describe the current implementation baseline; the dated target above governs future delivery work. OpenAPI now carries IMPLEMENTATION_CONTRACT, not the former draft-read-only status.

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
| Operator configuration | config/SubscriptionOperatorConfig, tools/subscription/README.md | Explicit external versioned manifest and account-history provenance; shares publication GLOBAL revision, no direct counter imports |
| Admin configuration | subscription/controller/AdminConfigurationController, service/AdminConfigurationService | ADMIN-only typed reads/history/preview; immutable complete bundles, CAS, actor-key receipts, canonical historical quota metadata; no account/bootstrap HTTP API |
| Admin effects | FREE next Vietnam midnight; TRIAL/PREMIUM NOW or future AT | New periods only for TRIAL/PREMIUM; existing snapshots unchanged; FREE retains selective activation event and accepted ADR-0001 delivery risk |
| Payment declarations | payment/contract/PaymentSucceeded, PaymentRefundSucceeded | Plain immutable facts plus compile-only test consumer; no production publisher, handler, paid lifecycle, charging or reconciliation |
| Runtime | tools/subscription/compose.replica.yaml and application-subscription-local.yaml | Opt-in replica set on loopback27018, separate volume/database, transaction manager enabled; default standalone runtime unchanged |
| Test-only producers | src/test/java/fit/iuh/engolearn/quota/QuotaIntegrationTest.java | Fake owning-feature callers use real Quota and isolated replica Mongo; no actual audio/assessment module |

For shortened feature paths, prefix src/main/java/fit/iuh/engolearn/. Architecture roots include the approved contract-only payment feature. Quota depends on subscription.contract; Subscription publishes events without a reverse Quota dependency. Payment has no production dependency on Subscription; the future owning consumer direction is Subscription to payment.contract.

## HTTP contract implemented

| Route | Access / behavior |
| --- | --- |
| GET /api/v1/subscription/plans | Public; data.items with configured benefits and billing options |
| GET /api/v1/subscription/me | Authenticated; current tier/status, explicit nullable period dates, eligibility/reason and immutable currentBenefits |
| GET /api/v1/quota/me/usage | Authenticated; data.items with actual used/limit/remaining, extensible identifiers, unit/period, windowStartAt/windowEndsAt/allowanceStartAt and status |
| POST /api/v1/subscription/me/trial | Authenticated; empty JSON object, UUID Idempotency-Key; 201 creation/200 accepted replay; original period values, no extension |
| GET /api/v1/subscription/admin/catalog | ADMIN; global revision, canonical quota metadata and supported scopes |
| GET /api/v1/subscription/admin/configurations/{scope} | ADMIN; current/pending immutable bundles and activation state |
| GET /api/v1/subscription/admin/configurations/{scope}/versions | ADMIN; bounded version history |
| GET /api/v1/subscription/admin/configurations/{scope}/versions/{version} | ADMIN; immutable version detail |
| POST /api/v1/subscription/admin/configurations/{scope}/preview | ADMIN; typed validation and calculated impact without writes |
| POST /api/v1/subscription/admin/configurations/{scope}/publications | ADMIN; expected global revision and UUID Idempotency-Key; atomic policy/plan/receipt writes,201 new/200 replay |

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
- Quota-consumption unknown-commit driver-error injection and population-scale all-users activation are not verified; actual producer integration is not claimed by test callers. Admin publication has a separate injected unknown-commit recovery test. All-users reset transaction capacity needs a separate scale decision before large rollout.
- Product prices/quotas, historic account migration and production sign-in are not approved by local verification. The user explicitly approved temporary isolated Free/Trial Speaking3600 DAILY and assessment Free1/Trial2 MONTHLY, with no Premium price.
- Payment runtime (including Trial-to-paid termination and payment return/reconciliation questions) remains later scope. M5 Admin source is now implemented; production rollout and population-scale activation remain unverified. Ledger has no TTL/automatic deletion.
- Keep current implementation and verification separate when updating this file. Add source/test/runtime evidence as work lands; never mark real Speaking/Proficiency integration complete from mock-caller tests.

## M3 declarations and M5 verification (2026-10-29)

- Scoped batch passed 66 tests with zero failures/errors/skips, including 12 Admin replica-set integration tests. It covers HTTP authorization and strict unknown/fractional fields, atomic rollback, exact high-resolution-clock replay, injected unknown-commit recovery, concurrent revision/key/canonical conflicts, read-only preview, timing/history and existing-period snapshots. Existing Quota, Subscription, security and architecture checks are included in that batch.
- The Payment consumer example compiled into target/test-classes. This is compile-only evidence for the two event declarations; no Payment runtime, publisher or consumer is claimed.
- One final full verify executed 300 tests: 4 failures, 37 errors, zero skips. It failed in Topic expectations/controller contexts and the intentionally unavailable-database context, consistent with the previous failure areas/counts. This is not a green whole-project build. No unrelated fix loop was started.
- Workspace evidence: .artifacts/subscription-payment-admin/backend-scoped-rerun2.log and backend-final-verify.log. Initial attempt failed before tests because Java could not read its cache in the restricted environment; rerun1 exposed a new test import ambiguity which was fixed before the successful rerun2. The scoped retry budget is exhausted.
- Real FE proxy/browser acceptance observed Guest401, User/ContentManager403 and Admin200. Browser preview kept revision0; saving unchanged Trial content created v2/revision1. Replay returned200 with the original publication/timestamps; a fresh key with stale revision returned409. Final Trial version remained2. Complete learner summary data stayed identical, including verification-trial-v1 and the original period dates.
- A second unchanged-content publication scheduled Free v2 at 2026-10-29T17:00:00Z (00:00 Vietnam on30October), producing global revision2. Free v1 remains current until then. Both content bundles retain all field values after canonical identifier ordering; no limit or price was changed and no usage fabricated. The browser showed scheduled/history/detail and blocked overwriting the pending scope. A dirty Trial form received REVISION_CONFLICT, retained its entered value and required explicit cancellation/reload.
- Live learner usage and public plans still returned200. The runtime was restarted without a provisioning manifest; agent-owned backend parent PID16892 runs subscription-local. Original browser User identity/usage page and default viewport were restored.
- Remaining verification: no live wait until midnight, actual network-loss/refetch-failure experiment, production Payment flow or population-scale activation. Free time selection and selective/idempotent Quota activation have component integration coverage; a single combined Admin-publication-to-scheduler-to-Quota boundary test has not been added. Do not claim that combined path was observed end to end. Scoped budget is exhausted, and no extra execution was started.
