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

@Document("payment_transactions")
@CompoundIndexes({
    @CompoundIndex(name="vnpay_payment_work", def="{'status':1,'provider_next_check_at':1,'provider_work_lease_until':1}"),
    @CompoundIndex(name="provider_payment_reference_unique", def="{'provider':1,'provider_reference':1}", unique=true, partialFilter="{'provider_reference':{'$type':'string'}}"),
    @CompoundIndex(name = "payment_purchase_unique", def = "{'purchase_reference':1}", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class PaymentTransaction extends BaseDocument {
    @Field("provider_tmn_code") private String providerTmnCode;
    @Field("provider_transaction_no") private String providerTransactionNo;
    @Field("provider_created_at") private Instant providerCreatedAt;
    @Field("provider_work_token") private String providerWorkToken;
    @Field("provider_work_lease_until") private Instant providerWorkLeaseUntil;
    @Field("provider_next_check_at") private Instant providerNextCheckAt;
    @Field("client_ip") private String clientIp;
    @Field("intent_created_at") private Instant intentCreatedAt;
    @Field("refund_revision") private long refundRevision;
    @Field("review_reason") private String reviewReason;
    @Field("purchase_reference")
    private String purchaseReference;
    @Field("user_id")
    private String userId;
    @Field("amount_vnd")
    private long amountVnd;
    @Field("currency")
    private String currency;
    @Field("status")
    private PaymentStatus status;
    @Field("checkout_url")
    private String checkoutUrl;
    @Field("expires_at")
    private Instant expiresAt;
    @Field("paid_at")
    private Instant paidAt;
    @Field("provider")
    private String provider;
    @Field("provider_reference")
    private String providerReference;
    @Field("provider_attempt_id")
    private String providerAttemptId;
}
