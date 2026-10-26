package fit.iuh.engolearn.learningpath.repository;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class QuestionPoolRepository {

    private final MongoTemplate mongoTemplate;

    public QuestionPoolRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long countActive(String microSkill, String level) {
        return mongoTemplate.count(Query.query(activeCriteria(microSkill, level)), "question_pool");
    }

    public List<Document> sampleActive(String microSkill, String level, int count) {
        return mongoTemplate.aggregate(
                Aggregation.newAggregation(
                        Aggregation.match(activeCriteria(microSkill, level)),
                        Aggregation.sample(count)
                ),
                "question_pool",
                Document.class
        ).getMappedResults();
    }

    public Map<String, Document> findPassages(Set<String> passageIds) {
        if (passageIds.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where("passage_id").in(passageIds));
        return mongoTemplate.find(query, Document.class, "question_pool_passages")
                .stream()
                .collect(Collectors.toMap(d -> d.getString("passage_id"), Function.identity()));
    }

    public void insertSnapshots(ObjectId pathId, ObjectId userId, List<Document> questions,
                                Map<String, Document> passages, Date createdAt) {
        List<Document> snapshots = questions.stream()
                .map(question -> snapshot(pathId, userId, question, passages, createdAt))
                .toList();
        mongoTemplate.insert(snapshots, "learning_path_questions");
    }

    private Criteria activeCriteria(String microSkill, String level) {
        return Criteria.where("micro_skill").is(microSkill)
                .and("level").is(level)
                .and("status").is("ACTIVE");
    }

    private Document snapshot(ObjectId pathId, ObjectId userId, Document question,
                              Map<String, Document> passages, Date createdAt) {
        String passageId = question.getString("passage_id");
        Document passage = passageId == null ? null : passages.get(passageId);
        if ("LISTENING".equals(question.getString("skill")) && passageId == null) {
            throw new IllegalStateException("Listening question is missing its passage");
        }
        if (passageId != null && (passage == null
                || !"ACTIVE".equals(passage.getString("status"))
                || !question.getString("skill").equals(passage.getString("skill"))
                || !question.getString("level").equals(passage.getString("level")))) {
            throw new IllegalStateException("Question Pool passage is unavailable or mismatched");
        }
        if ("LISTENING".equals(question.getString("skill"))) {
            Document audio = passage.get("audio", Document.class);
            if (audio == null || audio.getString("url") == null
                    || passage.getString("transcript") == null) {
                throw new IllegalStateException("Listening passage is missing audio or transcript");
            }
        }
        Document passageSnapshot = passage == null ? null : new Document("passage_id", passageId)
                .append("title", passage.getString("title"))
                .append("text", passage.getString("text"))
                .append("audio", passage.get("audio"))
                .append("transcript", passage.getString("transcript"))
                .append("revision", passage.get("revision"));
        return new Document("path_id", pathId)
                .append("user_id", userId)
                .append("question_id", question.getString("question_id"))
                .append("skill", question.getString("skill"))
                .append("micro_skill", question.getString("micro_skill"))
                .append("level", question.getString("level"))
                .append("passage", passageSnapshot)
                .append("prompt", question.getString("prompt"))
                .append("options", question.get("options"))
                .append("correct_option_id", question.getString("correct_option_id"))
                .append("explanation", question.getString("explanation"))
                .append("pool_revision", question.get("revision"))
                .append("created_at", createdAt);
    }
}
