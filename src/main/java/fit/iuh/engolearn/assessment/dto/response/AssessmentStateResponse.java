package fit.iuh.engolearn.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.*;
public record AssessmentStateResponse(String id,String status,long revision,Instant serverNow,Instant startedAt,Instant deadline,
            String phase,int unitIndex,String section,String series,int paperVersion,String passage,
            AssessmentQuestionResponse current,List<AssessmentQuestionResponse> preview,boolean audioAvailable,List<AssessmentCellResponse> progress,int savedCount,
            boolean developmentData,int rulesVersion,Instant audioStartsAt,List<AssessmentQuestionResponse> questions) {}
