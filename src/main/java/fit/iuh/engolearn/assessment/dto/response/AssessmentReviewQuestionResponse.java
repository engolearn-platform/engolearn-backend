package fit.iuh.engolearn.assessment.dto.response;

import java.util.List;
import jakarta.validation.constraints.*;
public record AssessmentReviewQuestionResponse(int number,String prompt,List<String> options,Integer selected,int correct,
            String explanation,String transcript,String section,String passage,int unitIndex,boolean audioAvailable) {}
