package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.subscription.dto.response.DevPurchaseResetResponse;
import fit.iuh.engolearn.subscription.model.PremiumPurchase;
public interface DevPurchaseResetService {
    boolean canReset(PremiumPurchase purchase);
    DevPurchaseResetResponse reset(String userId,String reference);
}
