# Purchase API (P03)

All routes require the authenticated learner. Success uses `ApiResponse`; failures use `ProblemDetail`. Responses have `Cache-Control: no-store`. These routes do not accept payment results or client-controlled amounts.

| Route | Behavior |
| --- | --- |
| `POST /api/v1/subscription/me/purchases` | UUID `Idempotency-Key`; exact JSON fields `planCode=PREMIUM` and `billingPeriod=MONTHLY|ANNUAL`. New intent returns 201; same user/key/selection returns 200 and the current original purchase. |
| `GET /api/v1/subscription/me/purchases/{reference}` | Owner-only purchase projection; a missing or another user's reference returns 404. |

Response data: `purchaseReference`, `status`, `billingPeriod`, `amountVnd`, `currency`, nullable `paymentId`, `checkoutUrl`, `paidAt`, `periodId`, `failureCode`, plus `createdAt` and `expiresAt`. Checkout URLs are exposed only while the purchase is payment-pending and its deadline has not passed.

Key errors: 400 `INVALID_REQUEST`; 401 `AUTHENTICATION_REQUIRED`; 404 `PURCHASE_NOT_FOUND`; 409 `IDEMPOTENCY_CONFLICT`, `PREMIUM_ALREADY_ACTIVE`, `PURCHASE_PENDING`, `OFFER_UNAVAILABLE`; 503 `SUBSCRIPTION_STATE_UNAVAILABLE`. A different payload with an existing intent key conflicts. A new key cannot bypass an unresolved purchase or active Premium period. Trial is allowed.

The server pins the published Premium plan, selected price and matching policy/benefits snapshot. Request idempotency never recalculates the accepted price. Purchase and checkout command Outbox commit together, with account revision and database uniqueness preventing competing requests from creating two blocking intents. Technical checkout lifetime defaults to configurable `engolearn.payment.checkout-lifetime=PT15M`; it is not a refund or entitlement policy duration.

Payment command intake commits its receipt and one setup-pending PaymentTransaction. A duplicate business reference cannot create another transaction, and a conflicting payload is preserved for review without rejecting/unlocking the legitimate purchase. No fake provider or automatic checkout preparation is wired in runtime. The internal preparation capability is for future verified provider adapters; tests call it with isolated fixtures. Until provider integration exists, accepted requests normally remain `PAYMENT_SETUP_PENDING`.

Local timeout never releases a purchase lock. A never-issued command first received after its deadline can be conclusively expired because no provider attempt exists. An already-issued/ambiguous transaction requires provider confirmation in later integration. Definitive Payment failure/expiry results release the lock; mismatches retain review evidence. Late setup/failure facts do not regress a committed successful purchase. No refund or history-list route is included.
