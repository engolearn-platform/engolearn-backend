package fit.iuh.engolearn.learningpath.dto.response;

import java.util.Date;
import java.util.List;

public record StartLearningPathDayResponse(
        String pathId,
        int weekNo,
        int dayNo,
        String status,
        Date startedAt,
        Date autoCloseAt,
        List<Question> questions
) {
    public record Question(
            int order,
            String questionId,
            String microSkill,
            String skill,
            Passage passage,
            String prompt,
            List<Option> options,
            String selectedOptionId
    ) {}

    public record Passage(String title, String text, Audio audio) {}

    public record Audio(String url, Integer durationSec, String format) {}

    public record Option(String optionId, String text) {}
}
