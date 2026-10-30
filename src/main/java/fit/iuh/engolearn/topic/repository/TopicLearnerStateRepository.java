package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicLearnerState;
import fit.iuh.engolearn.topic.model.vo.ResetReceiptVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TopicLearnerStateRepository {
    private final MongoTemplate mongo;

    public TopicLearnerState findById(String id) {
        return mongo.findById(id, TopicLearnerState.class);
    }

    public List<TopicLearnerState> findByUserId(String userId) {
        return mongo.find(Query.query(Criteria.where("user_id").is(userId)), TopicLearnerState.class);
    }

    public void insert(TopicLearnerState state) {
        mongo.insert(state);
    }

    public TopicLearnerState resetGeneration(String id, long expectedGeneration, ResetReceiptVo receipt) {
        Query query = Query.query(Criteria.where("_id").is(id).and("generation").is(expectedGeneration));
        Update update = new Update().inc("generation", 1).push("receipts", receipt);
        return mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), TopicLearnerState.class);
    }
}
