package fit.iuh.engolearn.learningpath.repository;

import java.time.Instant;

public record QuestionPoolQuestionRetirementSource(String questionId, int revision, String retiredBy, Instant retiredAt) {
}
