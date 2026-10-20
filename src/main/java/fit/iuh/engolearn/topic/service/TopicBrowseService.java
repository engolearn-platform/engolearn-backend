package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;

public interface TopicBrowseService {
    TopicPageResponse getAvailableTopics(GetAvailableTopicRequest request);
    TopicDetailResponse getTopicDetail(GetTopicDetailRequest request);
}