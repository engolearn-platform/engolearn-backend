package fit.iuh.engolearn.assessment.dto.response;

import java.util.List;
import jakarta.validation.constraints.*;
public record AssessmentReviewResponse(AssessmentResultResponse result,List<AssessmentReviewQuestionResponse> questions) {}
