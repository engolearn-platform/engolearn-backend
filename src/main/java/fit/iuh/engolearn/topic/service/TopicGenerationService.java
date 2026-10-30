package fit.iuh.engolearn.topic.service;



public interface TopicGenerationService {
    String id(String userId, String topicId);

    long current(String userId, String topicId);

    java.util.Map<String, Long> snapshot(String userId);

    boolean matches(String userId, String topicId, long generation);

    void require(String userId, String topicId, long generation);

    String scope(long generation);
}
