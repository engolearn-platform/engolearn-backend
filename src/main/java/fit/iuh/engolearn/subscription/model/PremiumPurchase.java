package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document("subscription_purchases")
@CompoundIndexes({
    @CompoundIndex(name = "purchase_history", def = "{'user_id':1,'created_at':-1,'_id':-1}"),
    @CompoundIndex(name = "purchase_reference_unique", def = "{'purchase_reference':1}", unique = true),
    @CompoundIndex(name = "purchase_request_unique", def = "{'user_id':1,'idempotency_key':1}", unique = true),
    @CompoundIndex(name = "one_blocking_purchase", def = "{'user_id':1}", unique = true, partialFilter = "{'blocks_new_purchase':true}"),
    @CompoundIndex(name = "purchase_payment_unique", def = "{'payment_id':1}", unique = true, partialFilter = "{'payment_id':{'$type':'string'}}")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class PremiumPurchase extends BaseDocument {
    @Field("intent_created_at") private Instant intentCreatedAt;
    @Field("period_end_at") private Instant periodEndAt;
    @Field("checkout_url") private String checkoutUrl;
    @Field("failure_code") private String failureCode;
    @Field("review_reason") private String reviewReason;
    @Field("purchase_reference")
    private String purchaseReference;
    @Field("user_id")
    private String userId;
    @Field("plan_code")
    private PlanCode planCode;
    @Field("billing_period")
    private BillingPeriod billingPeriod;
    @Field("amount_vnd")
    private long amountVnd;
    @Field("currency")
    private String currency;
    @Field("plan_version")
    private long planVersion;
    @Field("policy_version")
    private long policyVersion;
    @Field("plan_snapshot")
    private PlanCardVo planSnapshot;
    @Field("benefits_snapshot")
    private CurrentBenefitsVo benefitsSnapshot;
    @Field("policy_snapshot")
    private QuotaPolicySnapshotVo policySnapshot;
    @Field("selected_billing_option")
    private PlanBillingOptionVo selectedBillingOption;
    @Field("expires_at")
    private Instant expiresAt;
    @Field("refund_eligible_until")
    private Instant refundEligibleUntil;
    @Field("payment_id")
    private String paymentId;
    @Field("period_id")
    private String periodId;
    @Field("status")
    private PurchaseStatus status;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("request_fingerprint")
    private String requestFingerprint;
    @Field("blocks_new_purchase")
    private boolean blocksNewPurchase;
    @Field("paid_at")
    private Instant paidAt;
    @Field("refund_reference")
    private String refundReference;
    @Field("refund_id")
    private String refundId;
    @Field("refund_status")
    private PurchaseRefundStatus refundStatus;
}
