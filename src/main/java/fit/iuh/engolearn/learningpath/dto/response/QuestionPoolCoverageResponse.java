package fit.iuh.engolearn.learningpath.dto.response;

public record QuestionPoolCoverageResponse(
        String microSkill, String level, Long activeQuestionCountBefore, Long activeQuestionCountAfter,
        int minimumRequired, Boolean eligibleAfter) {
}
