package fit.iuh.engolearn.user.exception;

public class UserManagementFailure extends RuntimeException {
    public enum Reason {
        VALIDATION_ERROR("Invalid user management request"),
        USER_NOT_FOUND("User was not found"),
        ADMIN_TARGET_READ_ONLY("Administrator accounts are read-only"),
        USER_STATE_CONFLICT("The account changed. Review the current account before trying again."),
        EMAIL_ALREADY_EXISTS("Email already exists"),
        SERVICE_UNAVAILABLE("User management is temporarily unavailable");
        final String message;
        Reason(String message) { this.message = message; }
    }

    private final Reason reason;
    public UserManagementFailure(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
