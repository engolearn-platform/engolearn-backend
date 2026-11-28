package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.Date;
import java.util.List;

public record StartLearningPathDayResponse(
        String pathId,
        int weekNo,
        int dayNo,
        String status,
        Date startedAt,
        Date autoCloseAt,
        List<Question> questions,
        List<MicroSkillDto> microSkillCatalog
) {
    public StartLearningPathDayResponse { microSkillCatalog = List.copyOf(microSkillCatalog); }

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

    public record Passage(String title, String text, LearningPathAudioResponse audio) {}

    public record Option(String optionId, String text) {}
}
