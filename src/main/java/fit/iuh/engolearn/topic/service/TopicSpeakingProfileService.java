package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.dto.response.TopicSpeakingProfileResponse;

public interface TopicSpeakingProfileService {
    TopicSpeakingProfileResponse read(SpeakingScopeDto scope);
    TopicSpeakingProfileResponse save(SpeakingScopeDto scope, long revision, TopicSpeakingProfileDto profile);
    TopicSpeakingProfileResponse publish(SpeakingScopeDto scope, long revision);
}
