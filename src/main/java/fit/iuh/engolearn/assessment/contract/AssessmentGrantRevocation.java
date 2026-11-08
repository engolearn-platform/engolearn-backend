package fit.iuh.engolearn.assessment.contract;

/** Trusted integration wiring revokes a verified source period, preserving unrelated grants. */
public interface AssessmentGrantRevocation {
    void revoke(String userId, String sourceId);
}
