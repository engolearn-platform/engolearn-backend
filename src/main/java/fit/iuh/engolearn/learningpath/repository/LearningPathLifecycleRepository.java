package fit.iuh.engolearn.learningpath.repository;

import com.mongodb.client.result.UpdateResult;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LearningPathLifecycleRepository {

    private final MongoTemplate mongoTemplate;
    private boolean dueIndexReady;

    public LearningPathLifecycleRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private synchronized void ensureDueIndex() {
        if (dueIndexReady) return;
        mongoTemplate.indexOps(LearningPath.class).createIndex(
                new Index().on("is_active", Sort.Direction.ASC)
                        .on("next_run_at", Sort.Direction.ASC)
        );
        dueIndexReady = true;
    }

    public List<LearningPath> findDue(Instant now, int limit, List<ObjectId> excludedIds) {
        ensureDueIndex();
        Criteria criteria = Criteria.where("is_active").is(true)
                .and("next_run_at").lte(Date.from(now));
        if (!excludedIds.isEmpty()) {
            criteria.and("_id").nin(excludedIds);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "next_run_at"))
                .limit(limit);
        return mongoTemplate.find(query, LearningPath.class);
    }

    public Optional<LearningPath> findById(ObjectId id) {
        return Optional.ofNullable(mongoTemplate.findById(id, LearningPath.class));
    }

    public boolean apply(ObjectId id, String expectedStatus, Date expectedNextRunAt,
                         Integer expectedAnswered, Map<String, Object> fields) {
        Criteria criteria = Criteria.where("_id").is(id)
                .and("is_active").is(true)
                .and("status").is(expectedStatus)
                .and("next_run_at").is(expectedNextRunAt)
                .and("progress.answered").is(expectedAnswered);
        Update update = new Update();
        fields.forEach(update::set);
        UpdateResult result = mongoTemplate.updateFirst(new Query(criteria), update, LearningPath.class);
        return result.getModifiedCount() == 1;
    }
}
