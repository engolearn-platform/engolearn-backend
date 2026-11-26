package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.TopicPublishRequest;
import fit.iuh.engolearn.topic.dto.response.TopicPublishResponse;

public interface TopicPublishService {
    TopicPublishResponse publish(String topicId, TopicPublishRequest request);

    fit.iuh.engolearn.topic.dto.response.TopicUnpublishResponse unpublish(String topicId, TopicPublishRequest request);
}
