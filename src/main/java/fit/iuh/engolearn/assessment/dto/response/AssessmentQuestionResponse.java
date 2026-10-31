package fit.iuh.engolearn.assessment.dto.response;

import java.util.List;
import jakarta.validation.constraints.*;
public record AssessmentQuestionResponse(int number,String prompt,List<String> options,Integer selected) {}
