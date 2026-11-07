# VNPay Sandbox operations

The optional PAY 2.1.0 adapter/worker is implemented and disabled by default. The user reported successful Sandbox IPN and Premium activation. This documentation migration performed no provider calls or runtime tests; historical QueryDR response 99 is not proven resolved and real refund end-to-end remains unverified. Older test and environment observations are available in Git history at `docs/technical/subscription-quota/vnpay-setup.md`.

## Configure the backend and tunnel

Merge keys from [.env.vnpay.example](../../.env.vnpay.example) into the existing ignored `.env` or process environment; preserve Mongo/auth configuration. Never place real merchant secrets, full signatures or personal request captures in tracked docs. Restart after environment changes. Mapping is in [application.yaml](../../src/main/resources/application.yaml); validation and timing defaults are in [VnpaySettings](../../src/main/java/fit/iuh/engolearn/payment/infra/vnpay/VnpaySettings.java).

| Variable | Purpose |
| --- | --- |
| `VNPAY_TMN_CODE` | Issued eight-character alphanumeric merchant code |
| `VNPAY_HASH_SECRET` | Merchant HMAC signing secret, local environment only |
| `VNPAY_RETURN_URL` | Browser destination; local example `http://localhost:8080/api/payment/vnpay/return` |
| `VNPAY_SERVER_IP` | Server IP used for QueryDR/refund |
| `VNPAY_ENABLED` | Default false; enable only with complete merchant settings |
| `VNPAY_WORKER_ENABLED` | Default true; runs owned checkout/refund/reconciliation work only while VNPay itself is enabled |
| `VNPAY_PAY_URL` | Sandbox default `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_API_URL` | Sandbox default `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction` |

Register `GET https://YOUR_PUBLIC_HOST/api/payment/vnpay/ipn` with VNPay, tunnel upstream `http://localhost:8080`. IPN authenticates with HMAC-SHA512, not application login. The user's `ngrok-ipn-policy.yml` is outside this migration and permits the IPN route; preserve it. An IPN-only tunnel denies `/api/payment/vnpay/return`; use the loopback ReturnURL during local testing or deliberately configure an appropriate public destination. VNPay servers call the public IPN; the learner's browser follows Return. Update merchant IPN registration when the tunnel hostname changes.

Incomplete enabled settings fail startup. URLs require HTTPS except loopback HTTP; lease must exceed twice the network timeout and query interval must exceed five minutes. Defaults: worker poll 5 seconds, batch 10 per type, lease 60 seconds, timeout 10 seconds, query interval 6 minutes. These tune provider work under `engolearn.payment.vnpay`; shared Outbox delivery has separate `engolearn.messaging` configuration.

## Checks for a requested Sandbox exercise

These are operator instructions, not checks executed by this migration. A payment/refund exercise requires a deliberate request; do not resume historical transactions or issue refunds just because the adapter exists.

1. Start the existing configured backend on 8080 with transaction-capable MongoDB. Follow [operator setup](../../tools/subscription/README.md) for existing fixture/provisioning rules; do not reset histories or reapply an old initial manifest on every restart.
2. Inspect local/public `GET /api/payment/vnpay/ipn`: disabled returns raw `RspCode=99`; enabled unsigned requests return `97`. A raw response without redirect/interstitial proves reachability only, not payment confirmation or merchant registration.
3. For local frontend testing use `http://localhost:3000/subscription`. Dev CORS appends localhost/127.0.0.1:3000; production uses the configured `CORS_ALLOWED_ORIGINS`. Plain `403 Invalid CORS request` is an origin/configuration rejection, not a failed payment.
4. When authorized and merchant configuration is ready, create a purchase using its [HTTP contract](../technical/subscription/api-contract.md), a fresh intent key and authenticated owner. Poll owner detail until checkout URL appears, then complete Sandbox checkout. Remote address supplies provider client IP; forwarded headers need a trusted-proxy decision before use.
5. Check authoritative IPN or signed/correlated QueryDR processing, then owner purchase and `/me`. Browser Return is read-only and never activates. Do not replay Return as IPN.
6. Only when explicitly requested, exercise the ADMIN full-refund route. `REQUESTED`/Payment `UNKNOWN` retains current rights; only a verified final bound full-refund success revokes. An acknowledgment is insufficient. Never create a new refund merely to escape an unknown attempt.

## Diagnose uncertain results

| Observation | Meaning / next step |
| --- | --- |
| Return says success but purchase remains pending | Return is informational. Check matching verified IPN/result delivery and Payment-owned reconciliation state |
| Unsigned IPN 97 or unsigned query API 99 | Observation, not a financial outcome; retain purchase lock |
| Signed PAY status 02 with well-formed non-success response | Definitive failure when correlation checks pass; the implementation does not rely on a short code whitelist |
| Pending/reversal/fraud, not-found, invalid signature or transport error | Keep unresolved and reconcile the same attempt; no local deadline unlock |
| Delayed Subscription projection | Check recipient Outbox/receipt/review state; do not edit purchase/period state manually |
| Refund timeout or lost response | Keep stable attempt and `UNKNOWN`; query/reconcile, never blindly resend |
| Query describes only original successful payment | Does not prove refund completion |
| Signed refund observation without an exact known refund transaction binding | Manual/provider reconciliation remains necessary; do not assign an old observation to a new attempt |

Payment pins provider reference/merchant/original date before publishing checkout readiness. Provider dates have second precision; the adapter accepts matching-second lower bounds without altering `paidAt` or extending entitlement. Leased/fenced provider claims persist next-check time so restarts do not hammer QueryDR. Refund metadata is stored before entering `PROCESSING` and calling the provider once; a crash before the actual call can still need manual confirmation that nothing occurred.

Inspect only sanitized Payment diagnostics: `payment_transactions.provider_last_check` and `provider_check_count` record source/time/request ID/code/status/signature validity/classified result and bounded sanitized message. Transport classification includes `HTTP_ERROR`, `INVALID_RESPONSE`, `NETWORK_ERROR`, `TIMEOUT`, `INTERRUPTED`, optional HTTP status and exception class, without raw response, secret, signature or stack. Invalid-signature IPNs do not write per-payment diagnostics. These fields are not learner API data and do not themselves authorize transitions.

Historical QueryDR returned unsigned `99 Unexpected error`; request/checksum structure was compared with the official Java sample and no malformed field was established. Public unsigned reachability was observed but retained traffic did not prove matching-order IPN delivery. Later user-reported successful IPN/Premium activation proves the main path for that exercise, not recovery of the historical order. Do not guess a checksum change, unlock an order or mark that incident resolved from this evidence.

The historical TLS failure was traced to Oracle JDK 21.0.5 lacking the Sandbox chain's Sectigo root; an observed Corretto 21.0.8 runtime completed verified TLS. This is dated machine evidence, not a guarantee about the current process. Check the actual running JVM's `java.home` and keep IDE/Maven/terminal on a maintained JDK 21 with the required roots. Verified TLS does not resolve API99. Do not bypass certificate or hostname verification or import a rotating server leaf as a workaround.

Manual financial review/replay UI, automatic unknown-outcome resolution for every merchant refund shape and retention cleanup remain outside implemented operations. Keep unresolved records and necessary deduplication history. [DEV-PAYMENT-RESET-01](dev-purchase-reset.md) is a temporary local testing release with separate safeguards, not reconciliation.

Protocol references preserved from the earlier investigation (checked there on 2026-10-31, not fetched during migration): [PAY/IPN/Return](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [QueryDR/refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html), [error codes](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/), [Java sample archive](https://sandbox.vnpayment.vn/apis/files/vnpay_jsp.zip), [JDK 21.0.8 release notes](https://www.oracle.com/java/technologies/javase/21-0-8-relnotes.html).
