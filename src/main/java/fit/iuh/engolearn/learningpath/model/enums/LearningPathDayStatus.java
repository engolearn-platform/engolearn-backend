package fit.iuh.engolearn.learningpath.model.enums;

public enum LearningPathDayStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    SUBMITTED,
    CLOSED;

    public static LearningPathDayStatus effective(LearningPathDayStatus status, LearningPathDayCloseReason reason) {
        return status == CLOSED && reason == LearningPathDayCloseReason.SUBMITTED ? SUBMITTED : status;
    }
}
