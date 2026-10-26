package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PeriodicTestReadRepository {

    private final MongoTemplate mongoTemplate;

    public PeriodicTestReadRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<PeriodicTestSnapshot> findCompletedPeriodic(ObjectId attemptId, ObjectId userId) {
        Query attemptQuery = Query.query(Criteria.where("_id").is(attemptId)
                .and("user_id").is(userId)
                .and("type").is("PERIODIC")
                .and("status").is("COMPLETED"));
        Document attempt = mongoTemplate.findOne(attemptQuery, Document.class, "attempts");
        if (attempt == null) {
            return Optional.empty();
        }

        Document result = attempt.get("result", Document.class);
        String level = result == null ? null : result.getString("level");
        Query resultQuery = Query.query(Criteria.where("attempt_id").is(attemptId)
                .and("user_id").is(userId));
        List<PeriodicTestSnapshot.MicroSkillResult> results = mongoTemplate
                .find(resultQuery, Document.class, "micro_skill_results")
                .stream()
                .map(this::toResult)
                .toList();

        return Optional.of(new PeriodicTestSnapshot(
                attemptId,
                userId,
                level,
                attempt.getObjectId("test_set_id"),
                attempt.getDate("taken_at"),
                results
        ));
    }

    private PeriodicTestSnapshot.MicroSkillResult toResult(Document document) {
        return new PeriodicTestSnapshot.MicroSkillResult(
                document.getObjectId("_id"),
                document.getString("micro_skill"),
                document.getString("skill"),
                requiredNumber(document, "correct").intValue(),
                requiredNumber(document, "total").intValue(),
                requiredNumber(document, "percent").doubleValue(),
                document.getString("band"),
                Boolean.TRUE.equals(document.getBoolean("is_valid"))
        );
    }

    private Number requiredNumber(Document document, String field) {
        Object value = document.get(field);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("micro_skill_results." + field + " is missing or invalid");
    }
}
