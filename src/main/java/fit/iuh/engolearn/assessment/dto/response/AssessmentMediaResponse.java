package fit.iuh.engolearn.assessment.dto.response;

import jakarta.validation.constraints.*;
public record AssessmentMediaResponse(String mimeType,String base64) {}
