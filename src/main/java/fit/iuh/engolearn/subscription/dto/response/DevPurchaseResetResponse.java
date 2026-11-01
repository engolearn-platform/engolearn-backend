package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record DevPurchaseResetResponse(String purchaseReference,Instant resetAt) {}
