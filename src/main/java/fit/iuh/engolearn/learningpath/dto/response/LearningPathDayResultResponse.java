package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.Date;
import java.util.List;

public record LearningPathDayResultResponse(
        String pathId, int weekNo, int dayNo, String status, Date closedAt,
        int assigned, int answered, int correct, int wrong,
        Double completionRate, Double practiceScore, List<Question> questions,
        List<MicroSkillDto> microSkillCatalog
) {
    public LearningPathDayResultResponse { microSkillCatalog = List.copyOf(microSkillCatalog); }

    public record Question(
            int order, String questionId, String microSkill, String skill, Passage passage,
            String prompt, List<Option> options, String selectedOptionId,
            String correctOptionId, String explanation, String answerStatus
    ) {}

    public record Passage(String title, String text, LearningPathAudioResponse audio, String transcript) {}

    public record Option(String optionId, String text) {}
}
