package fit.iuh.engolearn.learningpath.contract.exception;

public class LearningPathCompanionReadException extends RuntimeException {
    public enum Reason { INVALID_QUERY, UNAVAILABLE, INCONSISTENT_STATE }
    private final Reason reason;

    public LearningPathCompanionReadException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
