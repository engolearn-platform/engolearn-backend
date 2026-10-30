package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.response.MyTopicPageResponse;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;

public interface TopicLearnerCatalogService {
    TopicPageResponse discover(GetAvailableTopicRequest request);

    MyTopicPageResponse mine(String status, Integer requestedLimit, String cursor);

    TopicDetailResponse detail(String topicId);
}
