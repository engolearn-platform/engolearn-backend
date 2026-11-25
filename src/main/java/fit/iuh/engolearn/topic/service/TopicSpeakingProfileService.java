package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse;
import java.util.List;

public interface TopicSpeakingProfileService {
    TopicSpeakingProfileResponse read(SpeakingScopeDto scope);
    TopicSpeakingProfileResponse save(SpeakingScopeDto scope, long revision, TopicSpeakingProfileDto profile);
    TopicSpeakingProfileResponse publish(SpeakingScopeDto scope, long revision);
    List<Violation> draftViolations(SpeakingScopeDto scope, String path);
}
