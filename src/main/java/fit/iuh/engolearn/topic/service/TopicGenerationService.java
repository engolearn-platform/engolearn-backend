package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.TopicLearnerState;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TopicGenerationService {
    private final MongoTemplate mongo;
    public TopicGenerationService(MongoTemplate mongo) { this.mongo = mongo; }
    public String id(String userId, String topicId) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((userId + "|topic-state|" + topicId).getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public long current(String userId, String topicId) {
        TopicLearnerState state = mongo.findById(id(userId, topicId), TopicLearnerState.class);
        return state == null ? 0 : state.getGeneration();
    }
    public java.util.Map<String, Long> snapshot(String userId) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        mongo.find(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId)), TopicLearnerState.class)
                .forEach(state -> result.put(state.getTopicId(), state.getGeneration()));
        return result;
    }
    public boolean matches(String userId, String topicId, long generation) { return current(userId, topicId) == generation; }
    public void require(String userId, String topicId, long generation) {
        if (!matches(userId, topicId, generation)) throw new TopicRequestException(409, "TOPIC_RESET", "This topic was reset; reload the topic");
    }
    public String scope(long generation) { return generation == 0 ? "" : "|generation|" + generation; }
}
