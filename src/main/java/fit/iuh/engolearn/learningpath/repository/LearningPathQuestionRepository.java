package fit.iuh.engolearn.learningpath.repository;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LearningPathQuestionRepository {

    private final MongoTemplate mongoTemplate;

    public LearningPathQuestionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Document> findSnapshots(ObjectId pathId, ObjectId userId, List<String> questionIds) {
        Query query = Query.query(Criteria.where("path_id").is(pathId)
                .and("user_id").is(userId)
                .and("question_id").in(questionIds));
        return mongoTemplate.find(query, Document.class, "learning_path_questions");
    }

    public List<Document> findAnswerKeys(ObjectId pathId, ObjectId userId, List<String> questionIds) {
        Query query = Query.query(Criteria.where("path_id").is(pathId)
                .and("user_id").is(userId)
                .and("question_id").in(questionIds));
        query.fields().include("question_id").include("correct_option_id");
        return mongoTemplate.find(query, Document.class, "learning_path_questions");
    }
}
