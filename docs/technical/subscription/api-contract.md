# Subscription and integration HTTP API

Paths below are implemented. Frontend routes return `ApiResponse<T>`, errors use `ProblemDetail`, and responses set `Cache-Control: no-store`. The authenticated principal supplies user/actor identity. Request shape is strict; clients cannot supply amounts, owners or financial results. Controller sources: [Subscription](../../../src/main/java/fit/iuh/engolearn/subscription/controller), [Quota](../../../src/main/java/fit/iuh/engolearn/quota/controller/QuotaController.java), [VNPay](../../../src/main/java/fit/iuh/engolearn/payment/infra/web/VnpayCallbackController.java).

## Learner surface

Prefix: `/api/v1/subscription` unless the complete path is shown.

| Method | Path | Actor | Request / response and behavior |
| --- | --- | --- | --- |
| GET | `/plans` | Public | `PlanListResponse`: currently published cards, server-owned prices and benefit metadata |
| GET | `/me` | Signed-in user | `MySubscriptionSummaryResponse`: time-resolved tier/status, period, benefits and Trial eligibility |
| POST | `/me/trial` | Signed-in user | UUID `Idempotency-Key`, empty object body; `TrialActivationResponse`, 201 new / 200 existing or replay; seven-day one-time Trial |
| POST | `/me/purchases` | Signed-in user | UUID key; `CreatePurchaseRequest` with exactly `planCode: PREMIUM`, `billingPeriod: MONTHLY` or `ANNUAL`; `PurchaseResponse`, 201 new / 200 replay |
| GET | `/me/purchases/{reference}` | Owner | `PurchaseResponse`; absent/other owner returns 404 |
| GET | `/me/purchases` | Owner | `limit` default 20, range 1..50; opaque `cursor`; `PurchaseHistoryResponse` has `items`, nullable `nextCursor` |
| GET | `/api/v1/quota/me/usage` | Signed-in user | `QuotaUsageSummaryResponse.items`: type/name, used/limit/remaining, unit, period, window end and availability status |
| POST | `/me/purchases/{reference}/dev-reset` | Owner, gated dev only | No request body/key; `DevPurchaseResetResponse`; [temporary release contract](../../runbooks/dev-purchase-reset.md) |

Purchase response fields: `purchaseReference`, `status`, `billingPeriod`, `amountVnd`, `currency`, `paymentId`, `checkoutUrl`, `expiresAt`, `paidAt`, `periodId`, `createdAt`, `failureCode`, `periodEndAt`, `refundReference`, `refundId`, `refundStatus`, `devResetAllowed`, `devResetAt`. `amountVnd` and `currency` are pinned at creation. `paymentId`, `checkoutUrl`, `paidAt`, `periodId`, `failureCode`, `periodEndAt`, `refundReference`, `refundId`, `refundStatus` and `devResetAt` are nullable according to lifecycle. `createdAt` represents pinned intent time. Checkout URL is exposed only in `PAYMENT_PENDING` before deadline; a hidden/expired URL does not establish unpaid status.

History is owner-filtered keyset pagination by stored `created_at DESC, _id DESC`. Pass `nextCursor` unchanged; null ends pagination. It includes failed/pending/refunded history and stored refund projections without querying Payment. `ACTIVE` records activation, not a promise that its calendar period still grants rights; use `/me` for current benefits.

Same user/key/selection returns the original purchase; changed selection conflicts. Active Premium, blocking purchase or pending refund prevents a new intent. Price, matching published plan/policy and benefits are pinned once. Trial permits purchase. Errors include 400 `INVALID_REQUEST`, 401 `AUTHENTICATION_REQUIRED`, 404 `PURCHASE_NOT_FOUND`, 409 `IDEMPOTENCY_CONFLICT`, `PREMIUM_ALREADY_ACTIVE`, `PURCHASE_PENDING`, `REFUND_PENDING`, `OFFER_UNAVAILABLE`, and 503 `SUBSCRIPTION_STATE_UNAVAILABLE`. Trial can additionally reject `TRIAL_ALREADY_USED`; malformed UUIDs and unknown body fields are invalid.

## Admin surface

All routes require ADMIN; unauthenticated is 401, a different role 403. Configuration prefix: `/api/v1/subscription/admin`.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/catalog` | `ConfigurationCatalogResponse`: scopes, billing/quota metadata |
| GET | `/configurations/{scope}` | `AdminConfigurationResponse`: current/pending immutable bundle and revision |
| GET | `/configurations/{scope}/versions` | `limit` default 20, `beforeVersion`; `ConfigurationHistoryResponse` |
| GET | `/configurations/{scope}/versions/{version}` | `ConfigurationVersionResponse` |
| POST | `/configurations/{scope}/preview` | `AdminPublicationRequest`; `ConfigurationPreviewResponse`; does not publish |
| POST | `/configurations/{scope}/publications` | UUID key and `AdminPublicationRequest`; `PublicationResultResponse`; 201 new / 200 replay |
| POST | `/purchases/{reference}/refund` | UUID key and `RefundRequest` exactly `{ "reason": "Requested by learner" }`, nonblank/max 1000; `RefundResponse`; 202 new / 200 replay |

Configuration scopes are `FREE`, `TRIAL`, `PREMIUM`. The request includes expected revision, effective mode/time, authored metadata/benefits, billing options and explicit quota rules. See the [OpenAPI schemas](../subscription-quota/quota-api.openapi.yaml) for the detailed configuration shape and error codes. Accepted replay precedes stale-revision/pending/time checks; changed key payload conflicts. FREE schedules next Vietnam midnight; TRIAL/PREMIUM `NOW` or future `AT` applies to new periods. No edit/delete/cancel saved-version endpoint exists.

Refund response: `refundReference`, `purchaseReference`, nullable `refundId`, `status`, `requestedAt`, nullable `completedAt` and `failureCode`. The same purchase/key/actor/reason returns the original attempt; changed actor/reason conflicts. Missing purchase is 404; unpaid/refunded/out-of-window is 409 `REFUND_INELIGIBLE`; pending is 409 `REFUND_PENDING`. The half-open window is seven days monthly, thirty days annually from pinned `paidAt`. Request acceptance queues work, not a completed refund. A definitive failed attempt needs a new key after eligibility is rechecked. See [refund flow](../../flows/subscription/payment-and-refund.md).

## Payment-owned protocol adapters

| Method | Complete path | Authentication / response |
| --- | --- | --- |
| GET | `/api/payment/vnpay/ipn` | Raw provider parameters; VNPay HMAC verification, no learner/Admin login; raw `VnpayIpnResponse` with `RspCode`, `Message` |
| GET | `/api/payment/vnpay/return` | Browser return; `VnpayReturnResponse` with signature validity, purchase reference and polling instruction; read-only |

No public HTTP endpoint accepts a force-success financial result or frontend usage writes. The [legacy OpenAPI](../subscription-quota/quota-api.openapi.yaml) is unchanged for frontend compatibility and covers only plans/self/Trial/quota and Admin configuration. Purchase/refund/dev-reset/provider routes are documented here and in their source DTOs; the YAML is not a complete backend API specification.
