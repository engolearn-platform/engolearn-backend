package fit.iuh.engolearn.assessment.contract.exception;

public class AssessmentHistoryInvalidQueryException extends IllegalArgumentException {
    public AssessmentHistoryInvalidQueryException() {
        super("Invalid assessment history query");
    }
}