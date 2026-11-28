package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.List;

public record LearningPathRecommendationResponse(
        State state,
        String message,
        String existingPathId,
        String level,
        int durationWeeks,
        int daysPerWeek,
        int questionsPerDay,
        List<MicroSkill> microSkills,
        List<MicroSkillDto> microSkillCatalog
) {
    public LearningPathRecommendationResponse { microSkillCatalog = List.copyOf(microSkillCatalog); }

    public enum State {
        AVAILABLE, NO_WEAK_SKILLS, NO_ELIGIBLE_SKILLS, ALREADY_CREATED
    }

    public record MicroSkill(
            String name,
            String skill,
            int correct,
            int total,
            double percent,
            String band,
            String displayName
    ) {
        public MicroSkill(String name, String skill, int correct, int total, double percent, String band) {
            this(name, skill, correct, total, percent, band, null);
        }
    }
}
