package fit.iuh.engolearn.learningpath.dto.response;

import java.util.Date;

public record SaveLearningPathAnswerDraftResponse(
        String questionId,
        String selectedOptionId,
        Date savedAt
) {}
