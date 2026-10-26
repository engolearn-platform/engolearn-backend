package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record LearningPathRecommendationResponse(
        State state,
        String message,
        String existingPathId,
        String level,
        int durationWeeks,
        int daysPerWeek,
        int questionsPerDay,
        List<MicroSkill> microSkills
) {
    public enum State {
        AVAILABLE, NO_WEAK_SKILLS, NO_ELIGIBLE_SKILLS, ALREADY_CREATED
    }

    public record MicroSkill(
            String name,
            String skill,
            int correct,
            int total,
            double percent,
            String band
    ) {
    }
}
