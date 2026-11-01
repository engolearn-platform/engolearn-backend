package fit.iuh.engolearn.payment.service.impl;
import fit.iuh.engolearn.payment.contract.DevPaymentReset;
import fit.iuh.engolearn.payment.model.enums.PaymentStatus;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor @Profile("dev & !prod")
@ConditionalOnProperty(name="engolearn.payment.dev-reset.enabled",havingValue="true")
public class DevPaymentResetImpl implements DevPaymentReset {
    private final PaymentTransactionRepository payments;
    private final RefundTransactionRepository refunds;
    private final MongoMessageTransactions transactions;
    public boolean release(String paymentId,String reference,String userId,Instant resetAt) {
        return transactions.required(()->{
            var payment=payments.findById(paymentId);
            if(payment==null || !reference.equals(payment.getPurchaseReference()) || !userId.equals(payment.getUserId()))return false;
            payments.guardRefund(paymentId);
            payment=payments.findById(paymentId);
            if(payment.getPaidAt()!=null || !Set.of(PaymentStatus.CHECKOUT_SETUP_PENDING,PaymentStatus.PENDING,
                    PaymentStatus.UNKNOWN,PaymentStatus.REVIEW_REQUIRED).contains(payment.getStatus()) ||
                    refunds.findBlockingOrSucceeded(paymentId)!=null)return false;
            payment.setDevResetAt(resetAt);payment.setDevResetBy(userId);
            payment.setDevResetPreviousStatus(payment.getStatus());payment.setDevResetPreviousReviewReason(payment.getReviewReason());
            payment.setStatus(PaymentStatus.REVIEW_REQUIRED);payment.setReviewReason("DEV_TEST_PURCHASE_RELEASED");
            payment.setCheckoutUrl(null);payments.save(payment);return true;
        });
    }
}
