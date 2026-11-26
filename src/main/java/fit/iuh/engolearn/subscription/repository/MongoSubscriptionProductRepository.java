package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.SubscriptionProduct;
import fit.iuh.engolearn.subscription.model.SubscriptionProductPublicationReceipt;
import fit.iuh.engolearn.subscription.model.SubscriptionProductVersion;
import fit.iuh.engolearn.subscription.model.vo.ProductContentVo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoSubscriptionProductRepository implements SubscriptionProductRepository {
    private final MongoTemplate mongo;

    public SubscriptionProduct findProduct(String planCode) {
        return mongo.findById(planCode, SubscriptionProduct.class);
    }

    public List<SubscriptionProduct> findProducts() {
        return mongo.findAll(SubscriptionProduct.class).stream()
                .sorted(java.util.Comparator.comparing(SubscriptionProduct::getPlanCode)).toList();
    }

    public SubscriptionProductVersion findVersion(String planCode, long version) {
        return mongo.findOne(Query.query(Criteria.where("plan_code").is(planCode).and("version").is(version)),
                SubscriptionProductVersion.class);
    }

    public List<SubscriptionProductVersion> findVersions(String planCode) {
        return mongo.find(Query.query(Criteria.where("plan_code").is(planCode))
                .with(Sort.by(Sort.Direction.DESC, "version")), SubscriptionProductVersion.class);
    }

    public SubscriptionProductPublicationReceipt findPublicationReceipt(String actor, String key) {
        return mongo.findOne(Query.query(Criteria.where("actor_id").is(actor).and("idempotency_key").is(key)),
                SubscriptionProductPublicationReceipt.class);
    }

    public SubscriptionProduct insertProduct(SubscriptionProduct product) {
        return mongo.insert(product);
    }

    public SubscriptionProductVersion insertVersion(SubscriptionProductVersion version) {
        return mongo.insert(version);
    }

    public SubscriptionProductPublicationReceipt insertPublicationReceipt(SubscriptionProductPublicationReceipt receipt) {
        return mongo.insert(receipt);
    }

    public boolean saveDraft(String planCode, long expectedRevision, ProductContentVo content) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(planCode)
                        .and("draft_revision").is(expectedRevision).and("archived").is(false)),
                new Update().set("draft_content", content).inc("draft_revision", 1).inc("purchase_fence", 1),
                SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean publish(String planCode, Long expectedVersion, long expectedRevision, long version) {
        Criteria current = expectedVersion == null ? Criteria.where("current_version").is(null)
                : Criteria.where("current_version").is(expectedVersion);
        return mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("_id").is(planCode), current,
                        Criteria.where("draft_revision").is(expectedRevision), Criteria.where("archived").is(false))),
                new Update().set("current_version", version).unset("draft_content")
                        .inc("draft_revision", 1).inc("purchase_fence", 1), SubscriptionProduct.class)
                .getModifiedCount() == 1;
    }

    public boolean setVisibility(String planCode, boolean hidden) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(planCode).and("archived").is(false)),
                new Update().set("visibility", hidden ? "HIDDEN" : "VISIBLE").inc("purchase_fence", 1),
                SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean setSaleStatus(String planCode, boolean stopped) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(planCode).and("archived").is(false)),
                new Update().set("sale_status", stopped ? "STOPPED" : "ON_SALE").inc("purchase_fence", 1),
                SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean archive(String planCode) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(planCode).and("archived").is(false)),
                new Update().set("archived", true).set("sale_status", "STOPPED").inc("purchase_fence", 1),
                SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean deleteDraft(String planCode, long expectedRevision) {
        return mongo.remove(Query.query(Criteria.where("_id").is(planCode).and("current_version").is(null)
                .and("draft_revision").is(expectedRevision).and("archived").is(false)), SubscriptionProduct.class)
                .getDeletedCount() == 1;
    }

    public boolean reservePurchase(String planCode, long expectedVersion) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(planCode).and("current_version").is(expectedVersion)
                        .and("sale_status").is("ON_SALE").and("archived").is(false)),
                new Update().inc("purchase_fence", 1), SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean replaceLegacySchedule(String planCode, Long expectedCurrentVersion, Long expectedScheduleVersion,
            long nextScheduleVersion) {
        Criteria criteria = Criteria.where("_id").is(planCode).and("archived").is(false)
                .and("current_version").is(expectedCurrentVersion).and("legacy_schedule_version").is(expectedScheduleVersion);
        return mongo.updateFirst(Query.query(criteria), new Update().set("legacy_schedule_version", nextScheduleVersion)
                .inc("purchase_fence", 1), SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean fenceLegacySchedule(String planCode, Long expectedCurrentVersion, Long expectedScheduleVersion) {
        Criteria criteria = Criteria.where("_id").is(planCode).and("archived").is(false)
                .and("current_version").is(expectedCurrentVersion).and("legacy_schedule_version").is(expectedScheduleVersion);
        return mongo.updateFirst(Query.query(criteria), new Update().inc("purchase_fence", 1),
                SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean applyLegacySchedule(String planCode, Long expectedCurrentVersion, long scheduleVersion) {
        Criteria criteria = Criteria.where("_id").is(planCode).and("archived").is(false)
                .and("current_version").is(expectedCurrentVersion).and("legacy_schedule_version").is(scheduleVersion);
        return mongo.updateFirst(Query.query(criteria), new Update().set("current_version", scheduleVersion)
                .inc("purchase_fence", 1), SubscriptionProduct.class).getModifiedCount() == 1;
    }

    public boolean setLegacyScheduleStatus(String planCode, long version, String status) {
        return mongo.updateFirst(Query.query(Criteria.where("plan_code").is(planCode).and("version").is(version)),
                new Update().set("legacy_schedule_status", status), SubscriptionProductVersion.class)
                .getModifiedCount() == 1;
    }
}
