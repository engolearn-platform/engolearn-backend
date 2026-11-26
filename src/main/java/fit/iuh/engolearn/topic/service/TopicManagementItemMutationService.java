package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.TopicItemCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemOrderRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVisibilityRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemsMutationResponse;

import java.util.Map;

public interface TopicManagementItemMutationService {
    TopicItemsMutationResponse create(String topicId, TopicItemCreateRequest request);

    TopicItemsMutationResponse update(String topicId, String itemId, Map<String, Object> request);

    TopicItemsMutationResponse reorder(String topicId, TopicItemOrderRequest request);

    TopicItemsMutationResponse visibility(String topicId, String itemId, TopicItemVisibilityRequest request);

    TopicItemsMutationResponse delete(String topicId, String itemId, Long revision);
}
