package fit.iuh.engolearn.subscription.model;
import fit.iuh.engolearn.subscription.dto.response.TrialActivation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_operation_receipts")
public record TrialReceipt(@Id String id, String userId, String operation, String idempotencyKey,
    String fingerprint, TrialActivation result) {}
