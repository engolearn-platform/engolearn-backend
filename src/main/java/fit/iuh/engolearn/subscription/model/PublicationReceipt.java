package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subscription_publication_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PublicationReceipt extends BaseDocument {
    @Field("fingerprint")
    private String fingerprint;
    @Field("environment")
    private String environment;
}
