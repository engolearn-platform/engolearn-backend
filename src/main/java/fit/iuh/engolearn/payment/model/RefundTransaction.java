package fit.iuh.engolearn.payment.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.payment.model.enums.*;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document("payment_refunds")
@CompoundIndexes({
    @CompoundIndex(name="vnpay_refund_work", def="{'status':1,'provider_next_check_at':1,'provider_work_lease_until':1}"),
    @CompoundIndex(name="refund_provider_attempt_unique", def="{'provider_attempt_id':1}", unique=true, partialFilter="{'provider_attempt_id':{'$type':'string'}}"),
    @CompoundIndex(name = "refund_reference_unique", def = "{'refund_reference':1}", unique = true),
    @CompoundIndex(name = "one_blocking_refund", def = "{'payment_id':1}", unique = true, partialFilter = "{'blocks_new_refund':true}"),
    @CompoundIndex(name = "one_successful_full_refund", def = "{'payment_id':1}", unique = true, partialFilter = "{'status':'SUCCEEDED'}")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class RefundTransaction extends BaseDocument {
    @Field("provider_tmn_code") private String providerTmnCode;
    @Field("provider_transaction_no") private String providerTransactionNo;
    @Field("provider_created_at") private Instant providerCreatedAt;
    @Field("provider_work_token") private String providerWorkToken;
    @Field("provider_work_lease_until") private Instant providerWorkLeaseUntil;
    @Field("provider_next_check_at") private Instant providerNextCheckAt;

    @Field("failure_code") private String failureCode;
    @Field("completed_at") private Instant completedAt;
    @Field("review_reason") private String reviewReason;
    @Field("refund_reference")
    private String refundReference;
    @Field("payment_id")
    private String paymentId;
    @Field("purchase_reference")
    private String purchaseReference;
    @Field("user_id")
    private String userId;
    @Field("amount_vnd")
    private long amountVnd;
    @Field("currency")
    private String currency;
    @Field("refund_type")
    private RefundType refundType;
    @Field("status")
    private RefundStatus status;
    @Field("requested_by")
    private String requestedBy;
    @Field("reason")
    private String reason;
    @Field("requested_at")
    private Instant requestedAt;
    @Field("refunded_at")
    private Instant refundedAt;
    @Field("blocks_new_refund")
    private boolean blocksNewRefund;
    @Field("provider_attempt_id")
    private String providerAttemptId;
}
