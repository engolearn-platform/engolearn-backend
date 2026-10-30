package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.subscription.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class MongoPurchaseRepository implements PurchaseRepository {
    private final MongoTemplate mongo;
    public PremiumPurchase findByIntent(String userId, String key) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId).and("idempotency_key").is(key)), PremiumPurchase.class);
    }
    public PremiumPurchase findByReference(String reference) {
        return mongo.findOne(Query.query(Criteria.where("purchase_reference").is(reference)), PremiumPurchase.class);
    }
    public PremiumPurchase findBlocking(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId).and("blocks_new_purchase").is(true)), PremiumPurchase.class);
    }
    public PremiumPurchase insert(PremiumPurchase purchase) { return mongo.insert(purchase); }
    public void save(PremiumPurchase purchase) { mongo.save(purchase); }
    public boolean guardAccount(SubscriptionAccount account) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(account.getId()).and("revision").is(account.getRevision())),
                new Update().inc("revision", 1), SubscriptionAccount.class).getModifiedCount() == 1;
    }
}
