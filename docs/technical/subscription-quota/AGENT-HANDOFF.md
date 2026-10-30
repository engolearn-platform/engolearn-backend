# Subscription / Quota handoff

Updated2026-10-30. Continue from the current implementation; do not restart exploration or redesign the environment.

## User working preference

Do the requested task directly and narrowly. Prefer existing tools and a minimal result check. Do not invent plans, infrastructure work, test suites or independent reviewers for simple seed/setup tasks. Ask before materially expanding scope. No reviewer unless explicitly requested.

## Committed snapshot

- Backend main:33c95e9 — Admin configuration, Payment declarations, main-dev setup and reusable fixtures.
- Frontend feat/explore-topics:3f1f504 — Admin plans/quota screens and shared shell/HTTP support.
- Earlier M1/M2/M4 work was already committed (backend7f36403 and predecessors; frontend e04763d and predecessors).
- No push. This handoff is committed separately after the feature snapshots.

## Implemented / still missing

- M1: learner plans, current benefits and quota APIs/screens; genuine FE-to-BE integration.
- M2: one-time seven-day Trial, immutable period benefits, idempotent retry, no duplicate extension.
- M4: real Quota interfaces/engine with test-only producer callers. Actual Speaking/Proficiency modules are NOT integrated. Speaking records measured seconds per audio turn, not session-end aggregation.
- M3: only PaymentSucceeded / PaymentRefundSucceeded declarations and compile example. NO checkout, purchase intent, payment callback handling, paid activation, refund processing or reconciliation. Showing a Premium price does NOT enable buying. UI explicitly says payment is not open.
- M5: ADMIN-only immutable configuration bundles, preview, publication, history/detail, revision conflicts, idempotency, scheduling and two FE management screens.
- Business docs remain authoritative. Reference images contain invented details; do not copy CEFR restrictions, metrics or infrastructure claims from them.

## Current manual-testing environment

- Main Mongo: localhost27017, database engolearn_db, container mongodb_srv, authenticated single-node engolearn-rs. Existing mongodb_data / mongodb_config volumes retained.
- Main compose now includes the replica startup/readiness scripts. Do NOT change/recreate this setup merely to seed more data. Old isolated27018 is no longer the manual-testing target.
- BE8080, FE3000, Mongo Express8081. Local ignored backend .env has spring.profiles.active=dev; dev config enables fixture auth and transactions. The .env itself is NOT committed.
- Normal backend startup from engolearn-backend: docker compose up -d, then mvn.cmd spring-boot:run. On this machine the previous agent used installed Maven with -Dmaven.repo.local=C:/Users/Hung Thinh/.m2/repository because wrapper/restricted cache access failed earlier.
- Last agent-started BE used the initial manifest startup argument. Normal later starts should OMIT that argument; the data is already persisted.
- FE development selector uses X-Dev-User-Id: dev-user / dev-cm / dev-admin. Guest sends no header. These are development identities, not production login.
- Routes: /subscription, /subscription/usage, /admin/subscription/plans, /admin/subscription/quotas. Admin screens require dev-admin; USER/CM receive403.

## Seed data and sharing

- Baseline: [dev-main-manifest.json](../../../tools/subscription/dev-main-manifest.json), published once through the operator runner; accounts have explicit NEW_ACCOUNT provenance. See [setup commands](../../../tools/subscription/README.md).
- Latest richer data: [seed-dev-catalog.mongosh.js](../../../tools/subscription/seed-dev-catalog.mongosh.js). This is the actual self-contained script applied to the main DB; run commands are at its top. It REQUIRES the baseline and deliberately rejects conflicting existing versions/states. Do not blindly reset data when that guard fires.
- Free:3600seconds/day and1 assessment/month. Trial:3600seconds/day and2/month. Premium:7200seconds/day and5/month;199000VND monthly,1590000VND annually. These are invented DEV fixtures, not approved production prices/policy.
- Latest script updates named demo catalog records, introduces Premium and replaces old verification placeholder copy with Vietnamese descriptions. It also patches display metadata on the known dev-admin Trial fixture, preserving its dates/IDs/rules/history. This direct dev fixture replacement is NOT normal immutable-policy business behavior.
- No fake usage or paid purchase was inserted. Last observed dev-user and dev-cm:Free/Trial-eligible. dev-admin:Trial2026-10-30T00:54:55.372Z through2026-11-06T00:54:55.372Z. Preserve existing account/Trial history unless user explicitly requests a reset.
- Main DB was backed up before replica conversion: /data/configdb/dev-main-before-replica-20261030.archive.gz in mongodb_config. No unrelated topic data was deleted.

## Verification / limits

- Prior scoped BE batch:66 tests passed, including12 Admin integration tests; real replica Mongo. FE TypeScript/build/lint passed; lint72warnings/0errors.
- Last full backend verify:300 tests,4failures,37errors,0skips in existing Topic/context areas. Do NOT claim whole-project green or loop-fix unrelated tests.
- Real browser core flow verified earlier: Trial activation/replay, Admin preview/save/history, stale conflict preserving input, role guards,390/1024 layout. After moving to main DB: plans/self/quota200, and quota200 for all three fixtures. After latest catalog seed: plans200 with Free+Premium/month+year; dev-admin summary200 and old placeholder text absent.
- No new build/test suite was run for the snapshot commits. Only staged whitespace checks. Do not reinterpret previous evidence as new execution.
- Still open: combined Admin publication -> scheduler -> Quota boundary test; real network-loss/ambiguous-save/refetch-failure experiment; production auth/account migration; population-scale activation. No Outbox/replay recovery: receipts prevent duplicates, not lost events.

## Main references

- [Business module](../../../../engolearn-business-docs-and-tools/docs/usecase_spec/subscription/00-module.md) and its linked rules/use cases.
- [Backend status](implementation-status.md), [technical design](quota-engine-design.md), [HTTP contract](quota-api.openapi.yaml), [Payment limits](payment-contract-handoff.md).
- [Learner FE handoff](../../../../engolearn-frontend/docs/subscription/user-ui-handoff.md), [Admin FE handoff](../../../../engolearn-frontend/docs/subscription/admin-ui-handoff.md).
- Workspace evidence: .artifacts/subscription-user-first/, .artifacts/subscription-payment-admin/, .artifacts/subscription-main-dev/. Older reports saying no Premium price or uncommitted describe earlier snapshots; the current seed and commits above supersede those statements.
- Pre-existing untracked backend docs/topic/api_contract.md, docs/topic/api_contract_short.md and material/ were deliberately left outside these commits. Do not stage/delete them as part of Subscription work.
