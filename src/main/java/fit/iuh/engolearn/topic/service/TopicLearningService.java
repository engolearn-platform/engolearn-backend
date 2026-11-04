package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.GetTopicItemContentRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicProgressRequest;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;

public interface TopicLearningService {
    TopicItemContentResponse getItemContent(String userId, GetTopicItemContentRequest request);

    TopicItemPositionResponse getItemPosition(String userId, GetTopicItemPositionRequest request);

    TopicItemPositionResponse saveItemPosition(String userId, SaveTopicItemPositionRequest request);

    TopicProgressResponse getTopicProgress(String userId, GetTopicProgressRequest request);
}
