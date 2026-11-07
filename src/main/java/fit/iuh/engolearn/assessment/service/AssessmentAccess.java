package fit.iuh.engolearn.assessment.service;
import java.util.Optional;
public interface AssessmentAccess {
    Optional<String> currentGrant(String user);
}
