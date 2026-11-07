package fit.iuh.engolearn.assessment.contract;
import java.util.Optional;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentBaselineDto;
/** A valid measured baseline; absence must not be replaced with a default level. */
public interface AssessmentBaselineSource {
    Optional<AssessmentBaselineDto> findValidBaseline(String userId);
}
