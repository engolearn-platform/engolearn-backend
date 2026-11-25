package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import java.util.List;

public record TopicSpeakingProfileResponse(long revision, TopicSpeakingProfileDto draft,
                                           TopicSpeakingProfileDto published, String contentVersion,
                                           List<TopicSpeakingResourceDto> resources) {}
