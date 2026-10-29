package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.model.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionIndexes {
    private final MongoTemplate mongo;
    public SubscriptionIndexes(MongoTemplate mongo) { this.mongo=mongo; }
    public void ensure() {
        for(Class<?> type: new Class<?>[]{PlanVersion.class,PolicyVersion.class,SubscriptionAccount.class,SubscriptionPeriod.class,TrialReceipt.class,PolicyState.class,PublicationReceipt.class})
            if(!mongo.collectionExists(type)) mongo.createCollection(type);
        mongo.indexOps(PlanVersion.class).createIndex(new Index().on("scope",Sort.Direction.ASC).on("version",Sort.Direction.ASC).unique());
        mongo.indexOps(PolicyVersion.class).createIndex(new Index().on("scope",Sort.Direction.ASC).on("version",Sort.Direction.ASC).unique());
        mongo.indexOps(SubscriptionPeriod.class).createIndex(new Index().on("userId",Sort.Direction.ASC).unique().partial(PartialIndexFilter.of(Criteria.where("kind").is("TRIAL"))).named("one_lifetime_trial"));
        mongo.indexOps(SubscriptionPeriod.class).createIndex(new Index().on("userId",Sort.Direction.ASC).on("periodStartAt",Sort.Direction.DESC));
        mongo.indexOps(TrialReceipt.class).createIndex(new Index().on("userId",Sort.Direction.ASC).on("operation",Sort.Direction.ASC).on("idempotencyKey",Sort.Direction.ASC).unique());
    }
}
