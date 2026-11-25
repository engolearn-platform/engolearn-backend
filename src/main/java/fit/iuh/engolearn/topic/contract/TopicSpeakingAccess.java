package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingAccessDto;

public interface TopicSpeakingAccess {
    /** Resolve the latest eligible released scope and profile without writing learner progress. */
    TopicSpeakingAccessDto resolve(String learnerId, SpeakingScopeDto scope);
}
