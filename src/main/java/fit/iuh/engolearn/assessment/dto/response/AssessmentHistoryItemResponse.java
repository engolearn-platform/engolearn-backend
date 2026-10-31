package fit.iuh.engolearn.assessment.dto.response;

import java.time.Instant;
import jakarta.validation.constraints.*;
public record AssessmentHistoryItemResponse(String id,String status,Instant finishedAt,String series,Integer paperVersion,
            Integer score,String level) {}
