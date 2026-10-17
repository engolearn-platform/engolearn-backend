package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicSummaryResponse;

import java.util.List;

public interface TopicBrowseService {
    List<TopicSummaryResponse> getAvailableTopics(GetAvailableTopicRequest request);
    TopicDetailResponse getTopicDetail(GetTopicDetailRequest request);
}