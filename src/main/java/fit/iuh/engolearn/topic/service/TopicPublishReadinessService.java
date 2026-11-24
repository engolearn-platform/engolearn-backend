package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse;

public interface TopicPublishReadinessService {
    TopicPublishReadinessResponse readiness(String topicId);
}
