package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.ResetTopicRequest;
import fit.iuh.engolearn.topic.dto.response.ResetTopicResponse;

public interface TopicResetService {
    ResetTopicResponse reset(String topicId, ResetTopicRequest body, String key);
}
