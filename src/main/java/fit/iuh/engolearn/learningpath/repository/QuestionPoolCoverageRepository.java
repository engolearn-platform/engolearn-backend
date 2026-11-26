package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.QuestionPoolCoverageFence;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QuestionPoolCoverageRepository {
    private final MongoTemplate mongo;

    public void ensure(QuestionPoolCoverageKey key) {
        Instant now = Instant.now();
        var query = Query.query(Criteria.where("_id").is(key.id()));
        try {
            mongo.upsert(query, new Update().setOnInsert("revision", 0L).setOnInsert("micro_skill", key.microSkill())
                    .setOnInsert("level", key.level()).setOnInsert("created_at", now).setOnInsert("updated_at", now), QuestionPoolCoverageFence.class);
        } catch (DuplicateKeyException firstUseRace) {
            if (!mongo.exists(query, QuestionPoolCoverageFence.class)) throw firstUseRace;
        }
    }

    public void fence(QuestionPoolCoverageKey key) {
        Instant now = Instant.now();
        try {
            var current = mongo.findById(key.id(), QuestionPoolCoverageFence.class);
            if (current == null) {
                throw new OptimisticLockingFailureException("Coverage must be initialized before the transaction snapshot");
            } else if (mongo.updateFirst(Query.query(Criteria.where("_id").is(key.id()).and("revision").is(current.getRevision())),
                    new Update().inc("revision", 1L).set("updated_at", now), QuestionPoolCoverageFence.class).getModifiedCount() != 1) {
                throw new OptimisticLockingFailureException("Coverage changed concurrently");
            }
        } catch (DuplicateKeyException firstUseRace) {
            throw new OptimisticLockingFailureException("Coverage changed concurrently", firstUseRace);
        }
    }
}
