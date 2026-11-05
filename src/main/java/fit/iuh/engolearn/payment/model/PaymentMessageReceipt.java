package fit.iuh.engolearn.payment.model;

import fit.iuh.engolearn.shared.messaging.model.MessageReceiptDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("payment_message_receipts")
@CompoundIndex(name = "message_consumer_unique", def = "{'message_id':1,'consumer_id':1}", unique = true)
@Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class PaymentMessageReceipt extends MessageReceiptDocument {}
