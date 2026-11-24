package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;

public record SpeakingScopeDto(SpeakingScopeType type, String topicId, String topicItemId) {
    public boolean valid() {
        return type != null && topicId != null && !topicId.isBlank()
                && (type == SpeakingScopeType.TOPIC ? topicItemId == null : topicItemId != null && !topicItemId.isBlank());
    }
}
