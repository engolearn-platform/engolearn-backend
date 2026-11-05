package fit.iuh.engolearn.payment.model.vo;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.payment.model.enums.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderCheckVo implements EmbeddedDocument {
    @Field("source") private ProviderCheckSource source;
    @Field("result") private ProviderCheckResult result;
    @Field("checked_at") private Instant checkedAt;
    @Field("request_id") private String requestId;
    @Field("response_code") private String responseCode;
    @Field("transaction_status") private String transactionStatus;
    @Field("signature_verified") private boolean signatureVerified;
    @Field("message") private String message;
    @Field("transport_failure") private ProviderTransportFailure transportFailure;
    @Field("http_status") private Integer httpStatus;
    @Field("cause_type") private String causeType;
}
