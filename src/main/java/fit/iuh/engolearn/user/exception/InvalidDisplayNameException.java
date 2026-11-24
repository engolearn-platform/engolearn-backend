package fit.iuh.engolearn.user.exception;

public class InvalidDisplayNameException extends RuntimeException {
    public InvalidDisplayNameException() {
        super("Display name must contain 1 to 80 characters, or be null to clear it");
    }
}
