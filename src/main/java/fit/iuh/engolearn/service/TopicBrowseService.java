package fit.iuh.engolearn.service;

import fit.iuh.engolearn.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.dto.response.TopicSummaryResponse;

import java.util.List;

public interface TopicBrowseService {
    List<TopicSummaryResponse> getAvailableTopics(GetAvailableTopicRequest request);
    TopicDetailResponse getTopicDetail(GetTopicDetailRequest request);
}