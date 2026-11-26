package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingResourceDto;
import java.util.List;

public record TopicSpeakingProfileResponse(long revision, TopicSpeakingProfileDto draft,
                                           TopicSpeakingProfileDto published, String contentVersion,
                                           List<TopicSpeakingResourceDto> resources,
                                           TopicSpeakingProfileDto initialProfile, String scopeTitle, String parentTitle,
                                           Long publishedProfileVersion, List<ResourceGroup> resourceGroups,
                                           List<TopicPublishReadinessResponse.Violation> publishViolations,
                                           List<TopicPublishReadinessResponse.Violation> publishedViolations,
                                           boolean draftMatchesPublished) {
    public record ResourceGroup(String itemId, String itemTitle, List<String> resourceIds) {}
}
