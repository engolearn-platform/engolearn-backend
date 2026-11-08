package fit.iuh.engolearn.assessment.contract;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
import java.util.Optional;
import java.util.Set;

/** Owner-scoped READY results, independent of display grants; reads never settle or grant access. */
public interface AssessmentResults {
    Optional<AssessmentResultDto> latest(String userId);

    /** Missing, foreign, unfinished and demonstration results outside dev are absent. */
    Optional<AssessmentResultDto> findCompleted(String userId, String assessmentAttemptId);

    Set<String> findCompletedAttemptIds(String userId, Set<String> assessmentAttemptIds);
}
