package fit.iuh.engolearn.subscription.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_publication_receipts")
public record PublicationReceipt(@Id String id, String fingerprint, String environment) {}
