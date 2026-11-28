package fit.iuh.engolearn.assessment.contract.exception;

public class AssessmentHistoryUnavailableException extends RuntimeException {
    public AssessmentHistoryUnavailableException() {
        super("Assessment history unavailable");
    }
}