# Subscription and integration HTTP API

Paths below are implemented. Frontend routes return `ApiResponse<T>`, errors use `ProblemDetail`, and responses set `Cache-Control: no-store`. The authenticated principal supplies user/actor identity. Request shape is strict; clients cannot supply amounts, owners or financial results. Controller sources: [Subscription](../../../src/main/java/fit/iuh/engolearn/subscription/controller), [Quota](../../../src/main/java/fit/iuh/engolearn/quota/controller/QuotaController.java), [VNPay](../../../src/main/java/fit/iuh/engolearn/payment/infra/web/VnpayCallbackController.java).

## Learner surface

Prefix: `/api/v1/subscription` unless the complete path is shown.

| Method | Path | Actor | Request / response and behavior |
| --- | --- | --- | --- |
| GET | `/plans` | Public | `PlanListResponse`: FREE and visible paid cards; paid cards include stable `planCode`, published `version` and `saleStatus` |
| GET | `/plans/{planCode}` | Public | Paid plan card, including a hidden plan for direct purchase links; archived/missing plans return 404 |
| GET | `/me` | Signed-in user | `MySubscriptionSummaryResponse`: time-resolved tier/status, period, benefits, Trial eligibility and additive `currentPlanCode` |
| POST | `/me/trial` | Signed-in user | UUID `Idempotency-Key`, empty object body; `TrialActivationResponse`, 201 new / 200 existing or replay; seven-day one-time Trial |
| POST | `/me/purchases` | Signed-in user | UUID key; `CreatePurchaseRequest` with `planCode`, displayed `expectedVersion`, and `billingPeriod: MONTHLY` or `ANNUAL`; `PurchaseResponse`, 201 new / 200 replay |
| GET | `/me/purchases/{reference}` | Owner | `PurchaseResponse`; absent/other owner returns 404 |
| GET | `/me/purchases` | Owner | `limit` default 20, range 1..50; opaque `cursor`; `PurchaseHistoryResponse` has `items`, nullable `nextCursor` |
| GET | `/api/v1/quota/me/usage` | Signed-in user | `QuotaUsageSummaryResponse.items`: type/name, used/limit/remaining, unit, period, window end and availability status |
| POST | `/me/purchases/{reference}/dev-reset` | Owner, gated dev only | No request body/key; `DevPurchaseResetResponse`; [temporary release contract](../../runbooks/dev-purchase-reset.md) |

Purchase response fields also include `planCode`, `planDisplayName` and `planVersion`. These fields remain available in purchase history. `amountVnd`, `currency`, plan code, display name, plan version, benefit set, quota rules and billing term are pinned at creation. `paymentId`, `checkoutUrl`, `paidAt`, `periodId`, `failureCode`, `periodEndAt`, `refundReference`, `refundId`, `refundStatus` and `devResetAt` are nullable according to lifecycle. `createdAt` represents pinned intent time. Checkout URL is exposed only in `PAYMENT_PENDING` before deadline; a hidden/expired URL does not establish unpaid status.

History is owner-filtered keyset pagination by stored `created_at DESC, _id DESC`. Pass `nextCursor` unchanged; null ends pagination. It includes failed/pending/refunded history and stored refund projections without querying Payment. `ACTIVE` records activation, not a promise that its calendar period still grants rights; use `/me` for current benefits.

Same user/key/selection returns the original purchase; changed selection conflicts. A stale `expectedVersion` returns 409 `PLAN_VERSION_STALE`; the learner must reload and reconfirm before retrying with a fresh key. Stopped or archived plans return `PLAN_SALE_STOPPED` or `PLAN_ARCHIVED`. Hidden plans remain purchasable by direct link. Active paid period, blocking purchase or pending refund prevents a new intent. Trial permits purchase. Sale status and published version are reserved against the product document in the same transaction as the purchase and checkout Outbox entry. Errors also include 400 `INVALID_REQUEST`, 401 `AUTHENTICATION_REQUIRED`, 404 `PURCHASE_NOT_FOUND`, 409 `IDEMPOTENCY_CONFLICT`, `PREMIUM_ALREADY_ACTIVE`, `PURCHASE_PENDING`, `REFUND_PENDING`, `OFFER_UNAVAILABLE`, and 503 `SUBSCRIPTION_STATE_UNAVAILABLE`. Trial can additionally reject `TRIAL_ALREADY_USED`; malformed UUIDs and unknown body fields are invalid.

## Admin surface

All routes require ADMIN; unauthenticated is 401, a different role 403. Prefix: `/api/v1/subscription/admin`.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/catalog` | `ConfigurationCatalogResponse`: scopes, billing/quota metadata |
| GET | `/configurations/{scope}` | `AdminConfigurationResponse`: current/pending immutable bundle and revision |
| GET | `/configurations/{scope}/versions` | `limit` default 20, `beforeVersion`; `ConfigurationHistoryResponse` |
| GET | `/configurations/{scope}/versions/{version}` | `ConfigurationVersionResponse` |
| POST | `/configurations/{scope}/preview` | `AdminPublicationRequest`; `ConfigurationPreviewResponse`; does not publish |
| POST | `/configurations/{scope}/publications` | UUID key and `AdminPublicationRequest`; `PublicationResultResponse`; 201 new / 200 replay |
| GET | `/products` | Product summaries (`hasDraft` included) plus the supported service registry |
| POST | `/products` | `{planCode}` creates an empty visible/on-sale draft product; it stays absent from the catalog and cannot be purchased until its first version is published |
| GET | `/products/{planCode}` | Product detail, `hasDraft`, current version, draft and history |
| GET | `/products/{planCode}/versions/{version}` | Historical immutable paid product version |
| PUT | `/products/{planCode}/draft` | `{expectedDraftRevision,content}`; validates registry codes and quota mappings |
| POST | `/products/{planCode}/preview` | `{expectedDraftRevision}`; returns draft content and effective time |
| POST | `/products/{planCode}/publish` | UUID key and `{expectedDraftRevision,replaceScheduled?,expectedScheduleVersion?,expectedScheduleEffectiveAt?}`; atomically publishes and consumes the draft; response includes `publishedVersion` |
| PATCH | `/products/PREMIUM/schedule` | `{expectedVersion,expectedEffectiveAt,effectiveAt}`; reschedules only a still-pending legacy Premium version |
| POST | `/products/PREMIUM/schedule/cancel` | `{expectedVersion,expectedEffectiveAt}`; cancels a still-pending legacy Premium schedule before its effective time |
| PATCH | `/products/{planCode}/visibility` | `{visibility: VISIBLE\|HIDDEN}`; hidden products can still be purchased by direct link |
| PATCH | `/products/{planCode}/sale` | `{saleStatus: ON_SALE\|STOPPED}`; stop/resume serializes with checkout |
| POST | `/products/{planCode}/archive` | Terminal archive; product history is retained |
| DELETE | `/products/{planCode}/draft` | 204; deletes only a never-published product |
| POST | `/purchases/{reference}/refund` | UUID key and `RefundRequest` exactly `{ "reason": "Requested by learner" }`, nonblank/max 1000; `RefundResponse`; 202 new / 200 replay |

Configuration scopes remain `FREE`, `TRIAL`, `PREMIUM`; FREE/TRIAL configuration behavior remains unchanged. Once PREMIUM is adopted into the product catalog, fixed-scope configuration reads/history redirect with `DYNAMIC_PRODUCTS_REQUIRED`; prior content remains readable through product version history. Legacy preview/publication writes return the same code. A legacy pending Premium schedule is copied into product history and reported as `legacySchedule` with `SCHEDULED`, `CANCELLED`, `APPLIED`, or `SUPERSEDED` status. It can be rescheduled or cancelled only before its effective time. An immediate Premium publish that would replace a still-pending schedule requires `replaceScheduled: true` together with the displayed `expectedScheduleVersion` and `expectedScheduleEffectiveAt`; otherwise it returns `LEGACY_SCHEDULE_REPLACEMENT_REQUIRED`. New paid products publish immediately and cannot be scheduled.

A product draft selects supported benefit service codes, quota rules for selected metered services, and monthly/annual prices. Publishing creates an immutable product version, advances its pointer, clears the draft and increments the draft revision. Exact key replay returns the original `publishedVersion` even if a later version is current. Hiding affects catalog visibility only; stopping sales blocks checkout. Archive is terminal. The supported registry initially contains `SPEAKING_DURATION`, `PROFICIENCY_CHECK_ATTEMPT`, and the unmetered `ASSESSMENT_FULL_ANALYSIS` right. Dynamic paid versions grant full analysis only when that benefit is present; legacy Premium and Trial snapshots keep their prior access behavior. See the [OpenAPI schemas](../subscription-quota/quota-api.openapi.yaml) for legacy configuration shapes and error codes.

Refund response: `refundReference`, `purchaseReference`, nullable `refundId`, `status`, `requestedAt`, nullable `completedAt` and `failureCode`. The same purchase/key/actor/reason returns the original attempt; changed actor/reason conflicts. Missing purchase is 404; unpaid/refunded/out-of-window is 409 `REFUND_INELIGIBLE`; pending is 409 `REFUND_PENDING`. The half-open window is seven days monthly, thirty days annually from pinned `paidAt`. Request acceptance queues work, not a completed refund. A definitive failed attempt needs a new key after eligibility is rechecked. See [refund flow](../../flows/subscription/payment-and-refund.md).

## Payment-owned protocol adapters

| Method | Complete path | Authentication / response |
| --- | --- | --- |
| GET | `/api/payment/vnpay/ipn` | Raw provider parameters; VNPay HMAC verification, no learner/Admin login; raw `VnpayIpnResponse` with `RspCode`, `Message` |
| GET | `/api/payment/vnpay/return` | Browser return; `VnpayReturnResponse` with signature validity, purchase reference and polling instruction; read-only |

No public HTTP endpoint accepts a force-success financial result or frontend usage writes. The [legacy OpenAPI](../subscription-quota/quota-api.openapi.yaml) is unchanged for frontend compatibility and covers only plans/self/Trial/quota and Admin configuration. Purchase/refund/dev-reset/provider routes are documented here and in their source DTOs; the YAML is not a complete backend API specification.
