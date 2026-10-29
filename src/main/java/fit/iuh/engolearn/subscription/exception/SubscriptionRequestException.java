package fit.iuh.engolearn.subscription.exception;
public class SubscriptionRequestException extends RuntimeException {
    private final int status;
    private final String code;
    public SubscriptionRequestException(int status, String code, String detail) { super(detail); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
}
