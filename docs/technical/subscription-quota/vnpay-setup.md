# VNPay Sandbox setup handoff

Integration code is available; VNPay is disabled by default. Real merchant/Sandbox end-to-end verification is pending configuration. This guide is the place to finish that configuration without changing business code.

## Exact IPN connection details

| Setting | Implemented value |
| --- | --- |
| HTTP method | GET |
| Backend route | /api/payment/vnpay/ipn |
| Local backend port | 8080 (src/main/resources/application.yaml, server.port) |
| Public URL to register with VNPay | https://YOUR_PUBLIC_HOST/api/payment/vnpay/ipn |
| Tunnel upstream | http://localhost:8080 |
| Callback authentication | VNPay HMAC signature, not a learner/Admin login |

The existing untracked ngrok-ipn-policy.yml already permits exactly GET on this IPN path. It was left untouched. The three connection details above are enough to route IPN traffic through that tunnel; merchant signing settings and a browser ReturnURL are separately required for payment.

## Fill these settings

Merge the keys from [.env.vnpay.example](../../../.env.vnpay.example) into the existing ignored backend .env, or set equivalent process environment variables. Preserve existing Mongo/auth settings. Do not replace the whole .env with the example. Put the actual secret only in the local .env/environment; the tracked example has blank merchant fields.

| Key | Value to supply |
| --- | --- |
| VNPAY_TMN_CODE | Sandbox merchant code issued by VNPay |
| VNPAY_HASH_SECRET | Sandbox merchant signing secret |
| VNPAY_RETURN_URL | Browser destination, e.g. http://localhost:8080/api/payment/vnpay/return when testing on this computer |
| VNPAY_SERVER_IP | IP address of the server making QueryDR/refund requests |
| VNPAY_ENABLED | Keep false while configuring; set true after all values above are filled |
| VNPAY_WORKER_ENABLED | true to prepare queued checkout and process/reconcile provider work |
| VNPAY_PAY_URL | Sandbox default is already configured; change only when deliberately switching environment |
| VNPAY_API_URL | Sandbox default is already configured; change only when deliberately switching environment |

Property mapping is in src/main/resources/application.yaml under engolearn.payment.vnpay. Merchant validation is in payment/infra/vnpay/VnpaySettings.java. Restart the backend after changing .env/environment. Enabling incomplete configuration fails startup rather than using fake credentials. No source edit is needed for the merchant/tunnel configuration.

The current tunnel policy exposes only IPN: it will deny /api/payment/vnpay/return on that same public host. For local browser testing use the localhost ReturnURL example, or deliberately allow the return path/use a separate suitable public URL. IPN is called by VNPay servers and must use the public HTTPS URL. ReturnURL is followed by the customer's browser. Keep these destinations distinct when configuring the IPN-only tunnel. A tunnel hostname change requires updating the registered public IPN URL; return configuration changes only if it uses that hostname.

## Verify the connection

1. Start/restart BE on 8080. GET http://localhost:8080/api/payment/vnpay/ipn needs no login and returns raw JSON with RspCode and Message. While disabled it returns RspCode 99 and "VNPay is disabled". With enabled settings, an unsigned request returns RspCode 97. These are reachability checks, not payment confirmations.
2. Point the HTTPS tunnel to localhost:8080 and register its exact IPN URL with VNPay. Check the public URL returns the same raw provider JSON, without a tunnel login/interstitial or redirect. POST is not the implemented IPN method.
3. After merchant settings are ready, enable VNPay and its worker, then use the existing authenticated POST /api/v1/subscription/me/purchases with a new UUID Idempotency-Key. Poll the owner detail route until checkoutUrl is available; open that URL. The backend captures request.getRemoteAddr for the provider client IP and pins checkout metadata. Forwarded headers are not accepted as client identity without trusted-proxy setup.
4. Complete a Sandbox payment. Browser return alone must leave activation awaiting authoritative confirmation. Verified IPN/QueryDR commits Payment success plus Outbox; Subscription then activates through the existing durable delivery. Check the owner purchase detail and subscription summary.
5. Exercise an ADMIN refund through the existing P06 route. REQUESTED/UNKNOWN keeps Premium until a verified completed full-refund result arrives. An API acknowledgment/processing status alone must not revoke it. If Sandbox does not expose a verifiable final refund transaction, leave it UNKNOWN and reconcile with VNPay; do not submit a fresh refund to escape that state.

No real Sandbox payment/refund or public tunnel call was performed during implementation. Record that evidence after configuration; local tests do not replace it. Production rollout is separate.

## Implemented behavior and recovery limits

PAY 2.1.0 uses HMAC-SHA512. Provider IPN is raw {RspCode,Message}; the read-only GET /api/payment/vnpay/return returns validSignature, purchaseReference and a polling instruction. These are provider adapters under payment.infra.web, outside frontend ApiResponse routes. They never accept client-controlled force-success requests.

The worker commits stable provider reference, merchant, original create date and signed checkout before publishing readiness. New requests carry the captured client IP; legacy unsent intents without it are setup-rejected safely, not issued with an invented IP. Price remains integer VND internally and is converted only at the adapter. Provider pay/refund dates have second precision; intent lower-bound checks tolerate the matching second without changing the verified timestamp or adding time to a Premium interval.

Owned work uses leased, fenced claims, with next-check time persisted at claim so a restart does not immediately hammer QueryDR. Defaults: poll 5 seconds, batch 10 per type, lease 60 seconds, network timeout 10 seconds, query interval 6 minutes. Configure through engolearn.payment.vnpay; the interval must exceed 5 minutes and lease exceed twice the timeout. QueryDR timeout, not-found, pending, reversal, signature problems and ambiguous results do not imply unpaid expiry or unlock a purchase. A signed financial contradiction is retained for review.

Refund metadata is persisted before the single transition to PROCESSING and the external call. Its stable provider request ID is 32 alphanumeric characters. Restart, timeout or lost response leads to QueryDR, never a blind refund resend. A crash before the actual network call may therefore require manual confirmation that no refund occurred. Only a signed, correlated FULL refund transaction with final success confirms completion; a query describing only the original successful payment cannot do that. The adapter includes the known refund transaction number when available. If a refund call times out before a signed response supplies that number, a later QueryDR result cannot bind an exact refund attempt using the original payment reference alone; the adapter retains UNKNOWN and holds a signed full-refund observation for manual reconciliation rather than assigning an old attempt to the new request. If merchant QueryDR cannot resolve that refund, manual/provider reconciliation remains required; no automatic guess or new refund reference is created. Manual review/replay UI and retention cleanup are not implemented.

Official protocol references checked 2026-10-31: [PAY/IPN/Return](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [QueryDR/refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html), [status/error codes](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/).
