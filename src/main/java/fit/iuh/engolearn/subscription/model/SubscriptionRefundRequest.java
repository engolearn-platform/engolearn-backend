package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.PurchaseRefundStatus;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.*;

@Document("subscription_refund_requests")
@CompoundIndexes({
    @CompoundIndex(name="refund_request_key_unique", def="{'purchase_reference':1,'idempotency_key':1}", unique=true),
    @CompoundIndex(name="refund_request_reference_unique", def="{'refund_reference':1}", unique=true),
    @CompoundIndex(name="refund_request_user_pending", def="{'user_id':1,'status':1}")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SubscriptionRefundRequest extends BaseDocument {
    @Field("refund_reference") private String refundReference;
    @Field("purchase_reference") private String purchaseReference;
    @Field("payment_id") private String paymentId;
    @Field("user_id") private String userId;
    @Field("idempotency_key") private String idempotencyKey;
    @Field("requested_by") private String requestedBy;
    @Field("reason") private String reason;
    @Field("requested_at") private Instant requestedAt;
    @Field("status") private PurchaseRefundStatus status;
    @Field("refund_id") private String refundId;
    @Field("failure_code") private String failureCode;
    @Field("completed_at") private Instant completedAt;
}
