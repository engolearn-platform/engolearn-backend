package fit.iuh.engolearn.subscription.service.impl;
import fit.iuh.engolearn.payment.contract.DevPaymentReset;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.subscription.dto.response.DevPurchaseResetResponse;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.PremiumPurchase;
import fit.iuh.engolearn.subscription.model.enums.PurchaseStatus;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.DevPurchaseResetService;
import java.time.Clock;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor @Profile("dev & !prod")
@ConditionalOnProperty(name="engolearn.payment.dev-reset.enabled",havingValue="true")
public class DevPurchaseResetServiceImpl implements DevPurchaseResetService {
    private final PurchaseRepository purchases;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRefundRepository refunds;
    private final DevPaymentReset payments;
    private final MongoMessageTransactions transactions;
    private final Clock clock;
    public boolean canReset(PremiumPurchase p) {
        return p.getDevResetAt()==null && p.isBlocksNewPurchase() && p.getPaymentId()!=null && p.getPeriodId()==null &&
                p.getPaidAt()==null && p.getRefundId()==null && p.getRefundReference()==null &&
                Set.of(PurchaseStatus.PAYMENT_SETUP_PENDING,PurchaseStatus.PAYMENT_PENDING,PurchaseStatus.REQUIRES_REVIEW).contains(p.getStatus());
    }
    public DevPurchaseResetResponse reset(String userId,String reference) {
        return transactions.independent(()->{
            var p=purchases.findByReference(reference);
            if(p==null || !userId.equals(p.getUserId()))throw new SubscriptionRequestException(404,"PURCHASE_NOT_FOUND","Purchase not found");
            if(p.getDevResetAt()!=null)return new DevPurchaseResetResponse(reference,p.getDevResetAt());
            if(!canReset(p) || refunds.findPending(userId)!=null)throw blocked();
            var account=subscriptions.findAccount(userId);
            if(account==null || !purchases.guardAccount(account))throw new OptimisticLockingFailureException("Account changed");
            var now=java.time.Instant.ofEpochMilli(clock.millis());
            if(!payments.release(p.getPaymentId(),reference,userId,now))throw blocked();
            p.setDevResetAt(now);p.setDevResetBy(userId);p.setDevResetPreviousStatus(p.getStatus());
            p.setDevResetPreviousReviewReason(p.getReviewReason());p.setStatus(PurchaseStatus.REQUIRES_REVIEW);
            p.setReviewReason("DEV_TEST_PURCHASE_RELEASED");p.setBlocksNewPurchase(false);p.setCheckoutUrl(null);purchases.save(p);
            return new DevPurchaseResetResponse(reference,now);
        });
    }
    private SubscriptionRequestException blocked() {
        return new SubscriptionRequestException(409,"DEV_RESET_NOT_ALLOWED","Only an unresolved unpaid test purchase without a refund can be released");
    }
}
