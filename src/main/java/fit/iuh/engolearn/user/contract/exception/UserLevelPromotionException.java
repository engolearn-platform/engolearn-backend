package fit.iuh.engolearn.user.contract.exception;

public class UserLevelPromotionException extends RuntimeException {
    public UserLevelPromotionException() { super("User level update is temporarily unavailable"); }
    public UserLevelPromotionException(Throwable cause) { super("User level update is temporarily unavailable", cause); }
}
