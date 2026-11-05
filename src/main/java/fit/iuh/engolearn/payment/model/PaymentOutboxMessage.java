package fit.iuh.engolearn.payment.model;

import fit.iuh.engolearn.shared.messaging.model.OutboxDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("payment_outbox")
@CompoundIndex(name = "delivery_due", def = "{'recipient':1,'status':1,'next_attempt_at':1,'lease_until':1}")
@Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class PaymentOutboxMessage extends OutboxDocument {}
