package fit.iuh.engolearn.assessment.contract;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionSummaryDto;
import java.util.Optional;

/** Owner-scoped latest result with only account-visible skill evidence. */
public interface AssessmentCompanionResults {
    Optional<AssessmentCompanionSummaryDto> latestForCompanion(String userId);
}
