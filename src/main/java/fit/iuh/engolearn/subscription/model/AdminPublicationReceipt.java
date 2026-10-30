package fit.iuh.engolearn.subscription.model;
import fit.iuh.engolearn.subscription.dto.response.AdminConfiguration.PublicationResult;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_admin_publication_receipts")
public record AdminPublicationReceipt(@Id String id,String actorId,String operation,String idempotencyKey,
    String fingerprint,Instant acceptedAt,PublicationResult result) {}
