# Dev purchase release

Owner-only POST /api/v1/subscription/me/purchases/{reference}/dev-reset. Available only for profile dev without prod, with PAYMENT_DEV_RESET_ENABLED=true; default false. Purchase detail advertises devResetAllowed and devResetAt. Production has no reset controller or reset business capability.

An unresolved, blocking purchase with a known Payment ID, no paid period and no refund can be released. Payment must also be unresolved and unrefunded; confirmed success is rejected even before its result reaches Subscription. Both module-owned writes join a single Mongo transaction through a dev-only Payment contract. This is a local operator exception to ordinary durable module messaging, not a financial result flow. Store actor/time/previous status/reason, mark review, remove checkout URL and purchase block, preserve history/Trial/entitlements/usage. Replays return the original reset time. A late IPN or queued event must not activate or reblock the released purchase. The reset does not call VNPay or emit payment-success/failure facts.

UI: capability-gated action in purchase detail, confirmation, per-request abort protection, remove only the matching saved intent after server success, then navigate to plans. No blanket account reset or deletion.

## Activation and limitations

Set PAYMENT_DEV_RESET_ENABLED=true in the ignored local .env and restart/reload BE with profile dev. The flag is false by default and cannot expose the feature with prod active, even alongside dev. Set it false to disable. The sample .env.vnpay.example documents the opt-in. Do not enable a test release workflow for real-money operations.

Financial/source state is checked again on POST; the advertised capability is a UI hint, not a reservation. A confirmed Payment success is rejected even if its Subscription result is delayed. Unknown-payment setup without a known source payment ID cannot be released yet. Pending refunds prevent release. Existing idempotency keys retain their original purchase; the UI clears only the matching old intent to generate a fresh key for the next user-initiated purchase.

Payment and Purchase persist dev_reset_at, dev_reset_by, dev_reset_previous_status and dev_reset_previous_review_reason. Account revision is advanced only for serialization; entitlement/Trial/quota/usage are not changed. No payment result event is forged. Late IPN remains financial review; late queued Subscription events are recorded for review without activating or reblocking the released purchase.

Historical verification and browser observations are available in Git history at `docs/technical/subscription-quota/dev-purchase-reset.md`. They were not rerun during this migration.

## Removal tracking: DEV-PAYMENT-RESET-01

Status: TEMPORARY / ACTIVE / DEV ONLY. This is a repository-local tracking ID, not an external issue. Review at VNPay integration sign-off; no removal date has been agreed. Maintainer: project owner. Purpose: unblock local Sandbox experiments while provider callback/reconciliation recovery is being stabilized.

Exit criteria: successful and failed Sandbox payments update the app correctly; duplicate callbacks are harmless; a missed callback can be recovered through verified QueryDR; the project owner confirms the workaround is no longer needed. The user's reported successful payment/Premium activation proves the main path, not all of these conditions. Provider refund end-to-end verification is a separate readiness item and has not been demonstrated by this feature.

Removal checklist:

- Remove DevPurchaseResetAction and its API client/type; remove the POST controller, dev business services, Payment contract and optional capability lookup.
- Remove PAYMENT_DEV_RESET_ENABLED from local setup/sample and application-dev.yaml. Production is already gated off independently of this cleanup.
- Retire the dev-only gate/reset tests and response capability field when its consumers are removed; update API and FE handoff docs together.
- Retain stored reset audit fields/history and late-event guards for already released records until those records are reconciled or an explicit archival/migration policy is approved. Removing the button must not allow an old paid event to activate/reblock a released order. Preserve intent clearing for confirmed historical releases while such records remain accessible.
- Record the cleanup commit(s) here and mark this tracking entry REMOVED only when completed. Do not delete unresolved orders as part of cleanup.

The user reported successful Sandbox IPN and Premium activation. This is user-reported integration evidence, not a new agent-run test, recovery of historical QueryDR 99, or proof of the complete exit criteria. Real Sandbox refund end-to-end remains unverified. See [VNPay operations](vnpay.md).
