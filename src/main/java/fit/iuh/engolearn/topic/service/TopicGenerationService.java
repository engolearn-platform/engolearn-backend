package fit.iuh.engolearn.topic.service;

import java.util.Collection;
import java.util.Map;

public interface TopicGenerationService {
    String id(String userId, String topicId);

    long current(String userId, String topicId);

    Map<String, Long> snapshot(String userId);

    Map<String, Long> snapshot(String userId, Collection<String> topicIds);

    boolean matches(String userId, String topicId, long generation);

    void require(String userId, String topicId, long generation);

    String scope(long generation);
}
