package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.shared.enums.CefrLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TopicSpeakingProfileDto(boolean enabled, CefrLevel level, String description, String aiRole,
                                      String learnerRole, List<String> suggestedTopics, List<String> selectedResourceIds,
                                      String fallbackReply, Hint fallbackHint) {
    public TopicSpeakingProfileDto {
        suggestedTopics = immutable(suggestedTopics);
        selectedResourceIds = immutable(selectedResourceIds);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public record Hint(String exampleEn, String explanationVi) {}
}
