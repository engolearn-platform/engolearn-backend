package fit.iuh.engolearn.assessment.dto.response;

import java.time.Instant;
import jakarta.validation.constraints.*;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
public record AssessmentPreparationResponse(boolean baselineValid,Instant eligibleAt,boolean cooldownSatisfied,
            AvailabilityResultDto quota,String activeAttemptId,boolean paperAvailable,boolean developmentData,
            String initialLevel,String currentLevel,String highestLevel) {}
