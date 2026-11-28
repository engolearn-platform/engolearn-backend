package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.TopicLearningContextDto;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningPositionDto;
import fit.iuh.engolearn.topic.contract.dto.TopicOverviewContextDto;
import java.util.Optional;

/** Read-only learner-visible context. Empty means missing, inaccessible or stale; dependency failures propagate. */
public interface TopicContextLookup {
    Optional<TopicOverviewContextDto> getOverview(String learnerId, String topicId);
    Optional<TopicLearningContextDto<?>> getLearningContext(String learnerId, String attemptId, TopicLearningPositionDto position);
}
