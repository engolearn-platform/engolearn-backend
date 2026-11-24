package fit.iuh.engolearn.user.contract;

import fit.iuh.engolearn.user.contract.dto.UserAssessmentProgressDto;
import java.util.Optional;

public interface UserAssessmentProgress {
    /** Looks up progress for this account. Older Assessment data remains readable during migration. */
    Optional<UserAssessmentProgressDto> find(String userId, boolean includeDevelopment);

    /** Updates the existing account profile inside the caller's transaction. */
    void save(String userId, UserAssessmentProgressDto progress);
}
