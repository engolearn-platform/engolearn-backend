package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.payment.contract.event.PaymentSucceededEvent;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class PaidActivationServiceImpl implements PaidActivationService {
    private final PurchaseRepository purchases;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMessageRepository reviews;
    private final SubscriptionEntitlementUpdates entitlements;
    private final Clock clock;
    public void apply(PaymentSucceededEvent event,MessageEnvelopeDto envelope) {
        PremiumPurchase purchase=purchases.findByReference(event.purchaseReference());
        if (purchase==null) {reviews.review(envelope,"PURCHASE_NOT_FOUND");return;}
        Instant paid=Instant.ofEpochMilli(event.paidAt().toEpochMilli());
        Instant requested=purchase.getIntentCreatedAt()==null?purchase.getCreatedAt():purchase.getIntentCreatedAt();
        if (!event.eventId().equals(envelope.messageId()) || !event.occurredAt().equals(envelope.occurredAt()) ||
                !event.purchaseReference().equals(envelope.aggregateId()) || !event.userId().equals(purchase.getUserId()) ||
                event.amountVnd()!=purchase.getAmountVnd() || !event.currency().equals(purchase.getCurrency()) ||
                purchase.getPaymentId()!=null && !purchase.getPaymentId().equals(event.paymentId()) ||
                paid.isBefore(requested.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)) || paid.isAfter(clock.instant())) {review(purchase,envelope,"PAYMENT_SUCCESS_MISMATCH");return;}
        if (purchase.getPeriodId()!=null) {
            if (!paid.equals(purchase.getPaidAt())) review(purchase,envelope,"CONFLICTING_PAID_AT");
            return;
        }
        if (purchase.getStatus()!=PurchaseStatus.PAYMENT_SETUP_PENDING && purchase.getStatus()!=PurchaseStatus.PAYMENT_PENDING) {
            review(purchase,envelope,"SUCCESS_AFTER_TERMINAL_PURCHASE");return;
        }
        SubscriptionAccount account=subscriptions.findAccount(purchase.getUserId());
        if (account==null || !purchases.guardAccount(account)) throw new OptimisticLockingFailureException("Account changed");
        ZonedDateTime start=paid.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        Instant end=(purchase.getBillingPeriod()==BillingPeriod.MONTHLY?start.plusMonths(1):start.plusYears(1)).toInstant();
        List<SubscriptionPeriod> periods=subscriptions.findPeriodsNewestFirst(purchase.getUserId());
        List<SubscriptionPeriod> trials=new ArrayList<>();
        for (SubscriptionPeriod period:periods) {
            if (period.getState()==PeriodState.REFUNDED || period.getState()==PeriodState.SUPERSEDED) continue;
            boolean overlap=period.getPeriodStartAt().isBefore(end) && paid.isBefore(period.getPeriodEndAt());
            if (!overlap) continue;
            if (period.getKind()!=PeriodKind.TRIAL || paid.isBefore(period.getPeriodStartAt())) {
                review(purchase,envelope,"PERIOD_OVERLAP_REQUIRES_REVIEW");return;
            }
            trials.add(period);
        }
        if (trials.size()>1) {review(purchase,envelope,"MULTIPLE_TRIAL_PERIODS");return;}
        for (SubscriptionPeriod trial:trials) subscriptions.supersedeTrial(trial.getId(),paid);
        SubscriptionPeriod premium=SubscriptionPeriod.builder().id(UUID.randomUUID().toString()).paymentId(event.paymentId())
                .userId(purchase.getUserId()).kind(purchase.getBillingPeriod()==BillingPeriod.MONTHLY?PeriodKind.MONTHLY:PeriodKind.ANNUAL)
                .state(clock.instant().isBefore(end)?PeriodState.ACTIVE:PeriodState.EXPIRED).periodStartAt(paid).periodEndAt(end)
                .snapshot(purchase.getPolicySnapshot()).benefits(purchase.getBenefitsSnapshot())
                .prices(List.of(purchase.getSelectedBillingOption())).build();
        subscriptions.createSubscriptionPeriod(premium);
        purchase.setPaymentId(event.paymentId());purchase.setPaidAt(paid);purchase.setPeriodEndAt(end);
        purchase.setPeriodId(premium.getId());purchase.setStatus(PurchaseStatus.ACTIVE);purchase.setBlocksNewPurchase(false);
        purchase.setCheckoutUrl(null);purchase.setFailureCode(null);
        purchase.setRefundEligibleUntil(paid.plus(Duration.ofDays(purchase.getBillingPeriod()==BillingPeriod.MONTHLY?7:30)));
        purchases.save(purchase);
        entitlements.refreshOwned(purchase.getUserId(),clock.instant());
    }
    private void review(PremiumPurchase purchase,MessageEnvelopeDto envelope,String reason) {
        reviews.review(envelope,reason);purchase.setReviewReason(reason);
        if (purchase.getPeriodId()==null) {
            purchase.setStatus(PurchaseStatus.REQUIRES_REVIEW);
            PremiumPurchase blocking=purchases.findBlocking(purchase.getUserId());
            purchase.setBlocksNewPurchase(blocking==null || blocking.getId().equals(purchase.getId()));
        }
        purchases.save(purchase);
    }
}
