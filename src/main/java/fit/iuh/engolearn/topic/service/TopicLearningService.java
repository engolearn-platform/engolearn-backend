package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.GetTopicItemContentRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicProgressRequest;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;

public interface TopicLearningService {

    TopicItemContentResponse getItemContent(GetTopicItemContentRequest request);

    TopicItemPositionResponse getItemPosition(GetTopicItemPositionRequest request);

    TopicItemPositionResponse saveItemPosition(SaveTopicItemPositionRequest request);

    TopicProgressResponse getTopicProgress(GetTopicProgressRequest request);
}
