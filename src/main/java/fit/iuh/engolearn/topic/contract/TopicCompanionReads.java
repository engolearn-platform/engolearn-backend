package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.*;

/** Read-only current learner catalog facts; caller supplies trusted learner identity. */
public interface TopicCompanionReads {
    TopicDiscoveryPageDto discover(String learnerId, TopicDiscoveryQueryDto query);
    TopicResumePageDto resume(String learnerId, TopicPageQueryDto query);
    TopicCompletionCountsDto counts(String learnerId);
}
