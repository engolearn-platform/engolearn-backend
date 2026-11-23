package fit.iuh.engolearn.assessment.service;
import java.util.Optional;
public interface AssessmentAccess {
    Optional<String> currentGrant(String user);

    static String sourceKey(String scope, String policySnapshotId, java.time.Instant effectiveAt) {
        return scope + ":" + policySnapshotId + ":" + effectiveAt;
    }
}
