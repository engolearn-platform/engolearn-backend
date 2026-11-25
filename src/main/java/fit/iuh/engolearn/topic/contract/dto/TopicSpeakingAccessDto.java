package fit.iuh.engolearn.topic.contract.dto;

import java.util.List;

public record TopicSpeakingAccessDto(Reason reason, Snapshot snapshot) {
    public enum Reason { SCOPE_FORBIDDEN, PROFILE_DISABLED }

    public record Snapshot(SpeakingScopeDto scope, String title, String topicTitle, String topicItemTitle,
                           String contentVersion, long profileVersion, TopicSpeakingProfileDto profile,
                           List<TopicSpeakingResourceDto> resources) {
        public Snapshot {
            resources = List.copyOf(resources);
        }
    }
}
