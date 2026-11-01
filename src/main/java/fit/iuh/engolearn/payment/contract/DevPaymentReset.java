package fit.iuh.engolearn.payment.contract;
import java.time.Instant;
public interface DevPaymentReset {
    /** Dev-only release joins the caller's Mongo transaction; false means correlation or financial eligibility failed. */
    boolean release(String paymentId,String purchaseReference,String userId,Instant resetAt);
}
