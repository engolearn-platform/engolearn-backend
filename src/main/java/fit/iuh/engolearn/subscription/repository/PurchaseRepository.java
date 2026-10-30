package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.subscription.model.*;
public interface PurchaseRepository {
    PremiumPurchase findByIntent(String userId, String key);
    PremiumPurchase findByReference(String reference);
    PremiumPurchase findBlocking(String userId);
    PremiumPurchase insert(PremiumPurchase purchase);
    void save(PremiumPurchase purchase);
    boolean guardAccount(SubscriptionAccount account);
}
