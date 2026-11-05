# Purchase and activation

```mermaid
sequenceDiagram
    actor Learner
    participant S as Subscription
    participant D as Durable delivery
    participant P as Payment
    participant V as VNPay
    participant Q as Quota
    Learner->>S: Create Premium purchase with stable intent key
    S->>S: Check eligibility and pin offer snapshot
    S->>D: Commit purchase and checkout command together
    D->>P: Deliver checkout command
    P->>P: Commit receipt and setup intent
    P->>P: Prepare signed checkout locally when enabled
    P->>D: Commit checkout readiness
    D->>S: Project ready checkout
    Learner->>S: Poll owner purchase detail
    S-->>Learner: Checkout URL before deadline
    Learner->>V: Complete checkout
    V-->>Learner: Browser Return is informational
    V->>P: Signed IPN
    alt Verified and correlated success
        P->>D: Commit success and result Outbox
        D->>S: Deliver payment success
        S->>S: Commit receipt, Trial supersede and Premium period
        S->>D: Commit effective entitlement fact
        D->>Q: Refresh projection without usage reset
    else Verified definitive failure or closed unpaid
        P->>D: Commit failure or expiry fact
        D->>S: Release matching pending purchase
    else Unknown or contradictory result
        P->>P: Retain unresolved state for reconciliation
    end
```

The checkout URL is locally constructed by Payment; preparation does not charge the learner. With VNPay disabled, setup intents remain queued. A valid same-user/key/selection replay returns the original purchase and pinned price. A new key cannot bypass active Premium, a blocking purchase or a pending refund. Trial permits purchase. Missing account provenance or published policy data fails unavailable rather than inventing eligibility.

```mermaid
stateDiagram-v2
    [*] --> PAYMENT_SETUP_PENDING
    PAYMENT_SETUP_PENDING --> PAYMENT_PENDING: checkout ready
    PAYMENT_SETUP_PENDING --> PAYMENT_SETUP_FAILED: setup rejected
    PAYMENT_SETUP_PENDING --> ACTIVE: verified success before readiness
    PAYMENT_PENDING --> ACTIVE: verified success
    PAYMENT_PENDING --> PAYMENT_FAILED: verified failure
    PAYMENT_PENDING --> PAYMENT_EXPIRED: confirmed closed unpaid
    PAYMENT_SETUP_PENDING --> PAYMENT_EXPIRED: never issued and deadline passed
    PAYMENT_PENDING --> REQUIRES_REVIEW: contradictory fact
    PAYMENT_SETUP_PENDING --> REQUIRES_REVIEW: incompatible success
```

This is the normal purchase projection, not every defensive transition or Payment's internal state. Local deadline/Return does not establish failure or unlock an issued/uncertain purchase. Success is duplicate-safe and may arrive before readiness; later failure cannot regress an activated purchase. Unmatched identities, amount/currency, impossible times or overlapping periods are held for review.

Activation supersedes an overlapping Trial at verified `paidAt`, retaining its original scheduled end and `supersededAt` history. Premium uses the purchase's immutable policy/benefits snapshot and starts at `paidAt`, adding one calendar month/year in `Asia/Ho_Chi_Minh` with end-of-month clamping. Delivery never extends the interval. A fully elapsed interval is stored as an expired period even though purchase status records successful activation as `ACTIVE`; current rights come from time-based entitlement resolution. Trial is never reissued or restored. Same-window usage is retained.

For the one-time seven-day Trial, the learner activates with an idempotency key: receipt, trial-used flag, period and entitlement Outbox commit atomically. A replay returns its original activation; an existing active Trial does not extend. Later Free fallback uses current effective policy. See [API](../../technical/subscription/api-contract.md) and [refund/uncertainty](payment-and-refund.md).
