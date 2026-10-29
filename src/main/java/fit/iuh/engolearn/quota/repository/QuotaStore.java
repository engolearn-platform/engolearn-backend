package fit.iuh.engolearn.quota.repository;

import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class QuotaStore {
    public static final String POLICIES = "quota_policy_snapshots";
    public static final String ENTITLEMENTS = "quota_entitlement_projection";
    public static final String COUNTERS = "quota_usage_counters";
    public static final String LEDGER = "quota_usage_ledger";
    public static final String RECEIPTS = "quota_policy_activation_receipts";
    private final MongoTemplate mongo;

    public QuotaStore(MongoTemplate mongo) { this.mongo = mongo; }

    public void ensureIndexes() {
        index(POLICIES, new Index().on("policySnapshotId", Direction.ASC).unique());
        index(POLICIES, new Index().on("policyScope", Direction.ASC).on("policyVersion", Direction.ASC).unique());
        index(ENTITLEMENTS, new Index().on("userId", Direction.ASC).unique());
        index(COUNTERS, new Index().on("userId", Direction.ASC).on("quotaType", Direction.ASC).unique());
        index(LEDGER, new Index().on("userId", Direction.ASC).on("sourceFeature", Direction.ASC)
                .on("idempotencyKey", Direction.ASC).unique());
        index(LEDGER, new Index().on("userId", Direction.ASC).on("quotaType", Direction.ASC).on("recordedAt", Direction.ASC));
        index(RECEIPTS, new Index().on("activationId", Direction.ASC).on("quotaType", Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("kind").is("RECEIPT"))));
        index(RECEIPTS, new Index().on("policyScope", Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("kind").is("SCOPE_ORDER"))));
    }

    private void index(String collection, Index index) { mongo.indexOps(collection).ensureIndex(index); }
    public Document one(String collection, Criteria criteria) { return mongo.findOne(Query.query(criteria), Document.class, collection); }
    public List<Document> find(String collection, Criteria criteria) { return mongo.find(Query.query(criteria), Document.class, collection); }
    public List<Document> all(String collection) { return mongo.findAll(Document.class, collection); }
    public void insert(String collection, Document document) { mongo.insert(document, collection); }
    public boolean update(String collection, Criteria criteria, Update update) {
        return mongo.updateFirst(Query.query(criteria), update, collection).getModifiedCount() == 1;
    }
}
