package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;
import java.util.List;

public record QuestionPoolQuestionDetailResponse(
        String questionId, QuestionPoolSkill skill, String microSkill, CefrLevel level,
        String passageId, String prompt, List<QuestionPoolQuestionOptionResponse> options,
        String correctOptionId, String explanation, QuestionPoolContentStatus status, Integer revision,
        String createdBy, String updatedBy, Instant createdAt, Instant updatedAt,
        QuestionPoolPassageDetailResponse passage, QuestionPoolQuestionPublicationReviewResponse publicationReview) {
    public QuestionPoolQuestionDetailResponse {
        options = options == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(options));
    }
}
