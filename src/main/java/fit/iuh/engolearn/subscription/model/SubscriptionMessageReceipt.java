package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.messaging.model.MessageReceiptDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("subscription_message_receipts")
@CompoundIndex(name = "message_consumer_unique", def = "{'message_id':1,'consumer_id':1}", unique = true)
@Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class SubscriptionMessageReceipt extends MessageReceiptDocument {}
