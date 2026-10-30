# Purchase API (P03)

All routes require the authenticated learner. Success uses `ApiResponse`; failures use `ProblemDetail`. Responses have `Cache-Control: no-store`. These routes do not accept payment results or client-controlled amounts.

| Route | Behavior |
| --- | --- |
| `POST /api/v1/subscription/me/purchases` | UUID `Idempotency-Key`; exact JSON fields `planCode=PREMIUM` and `billingPeriod=MONTHLY|ANNUAL`. New intent returns 201; same user/key/selection returns 200 and the current original purchase. |
| `GET /api/v1/subscription/me/purchases/{reference}` | Owner-only purchase projection; a missing or another user's reference returns 404. |
| `GET /api/v1/subscription/me/purchases?limit=20&cursor=...` | P05 owner-only history, including pending/failed/refunded intents, newest first. Returns `data.items` and nullable `data.nextCursor`. |

History uses bounded keyset pagination: default 20, range 1..50. Pass the opaque nextCursor unchanged; null means no further page. Ordering uses persisted creation time descending and ID descending to break millisecond ties, with a matching owner/time/ID index. Newer inserts between requests do not shift subsequent pages. Every query applies the authenticated owner, including when the client changes a cursor; no Payment collection read or per-user account bootstrap is needed. Malformed cursors/limits return 400 INVALID_REQUEST. Empty history returns 200 with an empty items array. Stored `refundReference`, `refundId` and `refundStatus` are included in both list/detail projections; P06 refund execution is still absent.

Response data: `purchaseReference`, `status`, `billingPeriod`, `amountVnd`, `currency`, nullable `paymentId`, `checkoutUrl`, `paidAt`, `periodId`, `failureCode`, plus `createdAt` and `expiresAt`. Checkout URLs are exposed only while the purchase is payment-pending and its deadline has not passed.

P04 adds nullable `periodEndAt`. Response `createdAt` uses the pinned intent creation instant (separate from storage auditing) and `paidAt`/`periodEndAt` describe the purchased calendar interval. `ACTIVE` means activation has committed; current rights still depend on that interval and are read through the subscription summary. A success delivered after the entire interval has ended creates historical EXPIRED period data, never a fresh month/year from delivery time.

Key errors: 400 `INVALID_REQUEST`; 401 `AUTHENTICATION_REQUIRED`; 404 `PURCHASE_NOT_FOUND`; 409 `IDEMPOTENCY_CONFLICT`, `PREMIUM_ALREADY_ACTIVE`, `PURCHASE_PENDING`, `OFFER_UNAVAILABLE`; 503 `SUBSCRIPTION_STATE_UNAVAILABLE`. A different payload with an existing intent key conflicts. A new key cannot bypass an unresolved purchase or active Premium period. Trial is allowed.

The server pins the published Premium plan, selected price and matching policy/benefits snapshot. Request idempotency never recalculates the accepted price. Purchase and checkout command Outbox commit together, with account revision and database uniqueness preventing competing requests from creating two blocking intents. Technical checkout lifetime defaults to configurable `engolearn.payment.checkout-lifetime=PT15M`; it is not a refund or entitlement policy duration.

Payment command intake commits its receipt and one setup-pending PaymentTransaction. A duplicate business reference cannot create another transaction, and a conflicting payload is preserved for review without rejecting/unlocking the legitimate purchase. No fake provider or automatic checkout preparation is wired in runtime. The internal preparation capability is for future verified provider adapters; tests call it with isolated fixtures. Until provider integration exists, accepted requests normally remain `PAYMENT_SETUP_PENDING`.

Local timeout never releases a purchase lock. A never-issued command first received after its deadline can be conclusively expired because no provider attempt exists. An already-issued/ambiguous transaction requires provider confirmation in later integration. Definitive Payment failure/expiry results release the lock; mismatches retain review evidence. Late setup/failure facts do not regress a committed successful purchase. No refund execution route is included.

P04 verified success handling commits receipt, Trial supersede, pinned Premium period, account sequence and entitlement Outbox together. Duplicate success cannot create another period or extend one. Provider outcome operations remain internal capabilities with no public result endpoint; real VNPay verification is P07. A period overlap that cannot be safely reconciled, including Trial started after the reported payment while delivery was delayed, is held for review rather than granting overlapping rights.
