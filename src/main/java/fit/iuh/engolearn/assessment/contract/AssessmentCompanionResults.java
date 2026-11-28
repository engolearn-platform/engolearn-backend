package fit.iuh.engolearn.assessment.contract;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionSummaryDto;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionHistoryPageDto;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentCompanionHistoryQueryDto;
import java.util.Optional;

/** Owner-scoped latest result with only account-visible skill evidence. */
public interface AssessmentCompanionResults {
    Optional<AssessmentCompanionSummaryDto> latestForCompanion(String userId);

    /** Dated READY results with current account-visible evidence; cursor is owner-bound. */
    AssessmentCompanionHistoryPageDto historyForCompanion(String learnerId, AssessmentCompanionHistoryQueryDto query);
}
