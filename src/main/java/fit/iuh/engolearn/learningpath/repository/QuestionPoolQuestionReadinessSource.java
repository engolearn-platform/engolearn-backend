package fit.iuh.engolearn.learningpath.repository;

import java.util.List;

public record QuestionPoolQuestionReadinessSource(
        String questionId, Integer revision, String status, String skill, String microSkill, String level,
        String passageId, String prompt, List<QuestionPoolQuestionOptionReadinessSource> options,
        String correctOptionId, String explanation, List<String> malformedFields) {
    public QuestionPoolQuestionReadinessSource {
        options = options == null ? null : List.copyOf(options);
        malformedFields = List.copyOf(malformedFields);
    }
}
