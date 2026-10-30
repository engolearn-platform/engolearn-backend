package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.subscription.model.*;
public interface PurchaseRepository {
    PremiumPurchase findByIntent(String userId, String key);
    PremiumPurchase findByReference(String reference);
    PremiumPurchase findBlocking(String userId);
    java.util.List<PremiumPurchase> history(String userId, int limit, java.time.Instant before, String beforeId);
    PremiumPurchase insert(PremiumPurchase purchase);
    void save(PremiumPurchase purchase);
    boolean guardAccount(SubscriptionAccount account);
}
