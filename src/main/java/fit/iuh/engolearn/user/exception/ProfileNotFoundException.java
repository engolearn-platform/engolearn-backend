package fit.iuh.engolearn.user.exception;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("User profile is not initialized");
    }
}
