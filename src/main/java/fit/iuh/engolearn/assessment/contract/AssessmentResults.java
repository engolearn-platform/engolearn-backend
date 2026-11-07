package fit.iuh.engolearn.assessment.contract;
import java.util.Optional;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
/** Learning consumers receive all five measured skills, independently of display grants. */
public interface AssessmentResults {Optional<AssessmentResultDto> latest(String userId);}
