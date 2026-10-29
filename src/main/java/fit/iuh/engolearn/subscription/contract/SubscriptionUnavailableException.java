package fit.iuh.engolearn.subscription.contract;

public class SubscriptionUnavailableException extends RuntimeException {
    public SubscriptionUnavailableException() { super("Subscription state cannot be confirmed"); }
    public SubscriptionUnavailableException(Throwable cause) { super("Subscription state cannot be confirmed", cause); }
}
