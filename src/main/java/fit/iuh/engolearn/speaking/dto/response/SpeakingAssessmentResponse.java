package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.speaking.model.enums.SpeakingAssessmentStatus;
import java.time.Instant;
import java.util.List;

public record SpeakingAssessmentResponse(String sessionId, SpeakingAssessmentStatus status, Criteria criteria,
                                         Feedback feedback, Instant finalizedAt) {
    public record Criteria(Criterion pronunciation, Criterion fluency, Criterion grammar, Criterion vocabulary) {}
    public record Criterion(String status, Double score, int maxScore, String reason, Integer sampleCount, Integer wordCount,
                            Long speechDurationMs, Component accuracy, Component range, List<Example> examples) {}
    public record Component(String status, String level, String reasonVi) {}
    public record Example(String turnId, String original, String suggested) {}
    public record Feedback(List<String> strengthsVi, List<String> improvementsVi,
                           List<String> recommendationsVi, List<Word> wordsToPractice) {}
    public record Word(String word, Double accuracyScore, String errorType) {}

    public static SpeakingAssessmentResponse pending(String sessionId, SpeakingAssessmentStatus status) {
        var acoustic = new Criterion(null, null, 100, null, null, null, null, null, null, List.of());
        var language = new Criterion(null, null, 5, null, null, null, null, null, null, List.of());
        return new SpeakingAssessmentResponse(sessionId, status, new Criteria(acoustic, acoustic, language, language),
                new Feedback(List.of(), List.of(), List.of(), List.of()), null);
    }
}
