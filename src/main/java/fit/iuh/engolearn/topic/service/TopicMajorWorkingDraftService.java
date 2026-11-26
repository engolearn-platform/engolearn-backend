package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.TopicItemWorkingDraftRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;

public interface TopicMajorWorkingDraftService {
    TopicItemResourceMutationResponse open(String topicId, String itemId, TopicItemWorkingDraftRequest request);
}
