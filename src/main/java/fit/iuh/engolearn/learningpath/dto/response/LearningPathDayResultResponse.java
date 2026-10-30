package fit.iuh.engolearn.learningpath.dto.response;

import java.util.Date;
import java.util.List;

public record LearningPathDayResultResponse(
        String pathId, int weekNo, int dayNo, String status, Date closedAt,
        int assigned, int answered, int correct, int wrong,
        Double completionRate, Double practiceScore, List<Question> questions
) {
    public record Question(
            int order, String questionId, String microSkill, String skill, Passage passage,
            String prompt, List<Option> options, String selectedOptionId,
            String correctOptionId, String explanation, String answerStatus
    ) {}

    public record Passage(String title, String text, Audio audio, String transcript) {}

    public record Audio(String url, Integer durationSec, String format) {}

    public record Option(String optionId, String text) {}
}
