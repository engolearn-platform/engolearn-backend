package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.*;

/**
 * Read-only current learner catalog facts; caller supplies trusted learner identity.
 * Invalid input is reported by {@link fit.iuh.engolearn.topic.contract.exception.TopicCompanionInvalidQueryException};
 * provider or inconsistent-state failures are not invalid queries.
 */
public interface TopicCompanionReads {
    TopicDiscoveryPageDto discover(String learnerId, TopicDiscoveryQueryDto query);
    TopicResumePageDto resume(String learnerId, TopicPageQueryDto query);
    TopicCompletionCountsDto counts(String learnerId);
}
