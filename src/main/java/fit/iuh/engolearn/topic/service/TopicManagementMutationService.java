package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;

import java.util.Map;

public interface TopicManagementMutationService {
    TopicManagementResponse create(Map<String, Object> body);

    TopicManagementResponse update(String topicId, Map<String, Object> body);
}
