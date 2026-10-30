package fit.iuh.engolearn.payment.model;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
@Document("payment_message_reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper=true)
public class PaymentMessageReview extends BaseDocument {
    @Field("message_id") private String messageId;
    @Field("payload") private String payload;
    @Field("reason") private String reason;
}
