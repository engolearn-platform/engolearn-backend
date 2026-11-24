package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.TopicItemContextRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVocabularyRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemExpressionsRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemQuizzesRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;

public interface TopicManagementResourceService {
    TopicItemResourceMutationResponse context(String topicId, String itemId, TopicItemContextRequest request);
    TopicItemResourceMutationResponse vocabulary(String topicId, String itemId, TopicItemVocabularyRequest request);
    TopicItemResourceMutationResponse expressions(String topicId, String itemId, TopicItemExpressionsRequest request);
    TopicItemResourceMutationResponse quizzes(String topicId, String itemId, TopicItemQuizzesRequest request);
}
