package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.model.TopicLearnerState;
import fit.iuh.engolearn.topic.repository.TopicLearnerStateRepository;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TopicGenerationServiceImpl implements TopicGenerationService {
    private final TopicLearnerStateRepository states;

    @Override
    public String id(String userId, String topicId) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((userId + "|topic-state|" + topicId).getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override
    public long current(String userId, String topicId) {
        TopicLearnerState state = states.findById(id(userId, topicId));
        return state == null ? 0 : state.getGeneration();
    }
    @Override
    public Map<String, Long> snapshot(String userId) {
        return index(states.findByUserId(userId));
    }

    @Override
    public Map<String, Long> snapshot(String userId, Collection<String> topicIds) {
        return index(states.findByUserIdAndIdIn(userId, topicIds.stream().map(topicId -> id(userId, topicId)).toList()));
    }

    private Map<String, Long> index(Collection<TopicLearnerState> rows) {
        Map<String, Long> result = new HashMap<>();
        rows.forEach(state -> result.put(state.getTopicId(), state.getGeneration()));
        return result;
    }
    @Override
    public boolean matches(String userId, String topicId, long generation) { return current(userId, topicId) == generation; }
    @Override
    public void require(String userId, String topicId, long generation) {
        if (!matches(userId, topicId, generation)) throw new TopicRequestException(409, "TOPIC_RESET", "This topic was reset; reload the topic");
    }
    @Override
    public String scope(long generation) { return generation == 0 ? "" : "|generation|" + generation; }
}
