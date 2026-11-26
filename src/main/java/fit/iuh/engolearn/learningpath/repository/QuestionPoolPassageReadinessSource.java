package fit.iuh.engolearn.learningpath.repository;

import java.util.List;

public record QuestionPoolPassageReadinessSource(
        String passageId, String skill, String level, String status, Integer revision,
        String title, String text, String transcript, String audioMediaId,
        boolean hasLegacyAudio, String legacyAudioUrl, List<String> malformedFields) {
    public QuestionPoolPassageReadinessSource {
        malformedFields = List.copyOf(malformedFields);
    }
}
