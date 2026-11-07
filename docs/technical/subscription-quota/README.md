# Subscription documentation compatibility router

Current documentation starts at the [Subscription technical entry](../subscription/README.md), [flows](../../flows/subscription/README.md), [VNPay runbook](../../runbooks/vnpay.md) and [dev purchase release](../../runbooks/dev-purchase-reset.md).

[quota-api.openapi.yaml](quota-api.openapi.yaml) is active and unchanged at this path for frontend compatibility. It covers plans/self/Trial/quota reads and Admin configuration; the [current API page](../subscription/api-contract.md) covers purchase/refund/provider routes omitted from it. [dev-purchase-reset.md](dev-purchase-reset.md) remains a short external-link redirect.

Earlier designs and dated test results are available in Git history under `docs/technical/subscription-quota/`, including superseded claims; do not use them as current status. Architectural decisions remain in [ADR-0001](../../architecture/decisions/ADR-0001-no-outbox-for-quota-mvp.md) and [ADR-0002](../../architecture/decisions/ADR-0002-durable-subscription-payment-quota-delivery.md).
