package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.payment.contract.dto.FullRefundCommandDto;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.dto.response.RefundResponse;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class SubscriptionRefundServiceImpl implements SubscriptionRefundService {
    private final SubscriptionRefundRepository refunds;
    private final PurchaseRepository purchases;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMessageRepository messages;
    private final SubscriptionTransactions transactions;
    private final SubscriptionEntitlementUpdates entitlements;
    private final ObjectMapper mapper;
    private final Clock clock;

    public Requested request(String actor,String reference,String key,String reason) {
        String normalized;
        try {
            normalized=UUID.fromString(key).toString();
            if (!normalized.equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw error(400,"INVALID_REQUEST","A UUID Idempotency-Key is required"); }
        for (int attempt=0;attempt<3;attempt++) {
            try { return transactions.execute(()->requestOwned(actor,reference,normalized,reason)); }
            catch (SubscriptionRequestException | SubscriptionUnavailableException failure) { throw failure; }
            catch (RuntimeException failure) {
                if (!retryable(failure) || attempt==2) throw new SubscriptionUnavailableException(failure);
                java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(50L*(attempt+1)).toNanos());
            }
        }
        throw new SubscriptionUnavailableException();
    }
    private Requested requestOwned(String actor,String reference,String key,String reason) {
        SubscriptionRefundRequest previous=refunds.findByKey(reference,key);
        if (previous!=null) {
            if (!previous.getRequestedBy().equals(actor) || !previous.getReason().equals(reason))
                throw error(409,"IDEMPOTENCY_CONFLICT","Refund key was used for another request");
            return new Requested(response(previous),false);
        }
        PremiumPurchase purchase=purchases.findByReference(reference);
        if (purchase==null) throw error(404,"PURCHASE_NOT_FOUND","Purchase was not found");
        guard(purchase.getUserId());
        if (refunds.findPending(purchase.getUserId())!=null)
            throw error(409,"REFUND_PENDING","An earlier refund is unresolved");
        Instant now=now();
        SubscriptionPeriod period=period(purchase);
        if (purchase.getStatus()!=PurchaseStatus.ACTIVE || period==null ||
                period.getState()==PeriodState.REFUNDED || period.getState()==PeriodState.SUPERSEDED ||
                purchase.getPaidAt()==null || now.isBefore(purchase.getPaidAt()) ||
                purchase.getRefundEligibleUntil()==null || !now.isBefore(purchase.getRefundEligibleUntil()))
            throw error(409,"REFUND_INELIGIBLE","Purchase is outside its product refund eligibility");
        String refundReference=UUID.randomUUID().toString();
        SubscriptionRefundRequest request=SubscriptionRefundRequest.builder().id(refundReference).refundReference(refundReference)
                .purchaseReference(reference).paymentId(purchase.getPaymentId()).userId(purchase.getUserId())
                .idempotencyKey(key).requestedBy(actor).reason(reason).requestedAt(now).status(PurchaseRefundStatus.REQUESTED).build();
        refunds.insert(request);
        purchase.setRefundReference(refundReference);purchase.setRefundId(null);
        purchase.setRefundStatus(PurchaseRefundStatus.REQUESTED);purchase.setStatus(PurchaseStatus.REFUND_PENDING);
        purchases.save(purchase);
        String id=UUID.randomUUID().toString();
        var command=new FullRefundCommandDto(id,1,now,refundReference,purchase.getPaymentId(),reference,purchase.getUserId(),actor,reason);
        messages.append(new MessageEnvelopeDto(id,"FULL_REFUND",1,reference,"payment.refund",now,mapper.writeValueAsString(command)));
        return new Requested(response(request),true);
    }
    public void apply(MessageEnvelopeDto envelope) {
        boolean success="PAYMENT_REFUND_SUCCEEDED".equals(envelope.messageType());
        String id,reference,refundId,paymentId,purchaseReference,userId,code;
        Instant occurred,completed;
        if (success) {
            var event=mapper.readValue(envelope.payload(),PaymentRefundSucceededEvent.class);
            id=event.eventId();reference=event.refundReference();refundId=event.refundId();paymentId=event.paymentId();
            purchaseReference=event.purchaseReference();userId=event.userId();occurred=event.occurredAt();completed=event.refundedAt();code=null;
        } else {
            var event=mapper.readValue(envelope.payload(),PaymentRefundFailedEvent.class);
            id=event.eventId();reference=event.refundReference();refundId=event.refundId();paymentId=event.paymentId();
            purchaseReference=event.purchaseReference();userId=event.userId();occurred=event.occurredAt();completed=event.failedAt();code=event.failureCode();
        }
        SubscriptionRefundRequest request=refunds.findByReference(reference);
        PremiumPurchase purchase=purchases.findByReference(purchaseReference);
        if (request==null || purchase==null || !id.equals(envelope.messageId()) || !occurred.equals(envelope.occurredAt()) ||
                !purchaseReference.equals(envelope.aggregateId()) || !purchaseReference.equals(request.getPurchaseReference()) ||
                !userId.equals(request.getUserId()) || !userId.equals(purchase.getUserId()) ||
                !paymentId.equals(request.getPaymentId()) || !paymentId.equals(purchase.getPaymentId()) ||
                request.getRefundId()!=null && !refundId.equals(request.getRefundId()) ||
                completed.isBefore(request.getRequestedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)) || completed.isAfter(clock.instant())) {
            messages.review(envelope,"REFUND_CORRELATION_CONFLICT");return;
        }
        PurchaseRefundStatus target=success?PurchaseRefundStatus.SUCCEEDED:PurchaseRefundStatus.FAILED;
        if (request.getStatus()==PurchaseRefundStatus.SUCCEEDED || request.getStatus()==PurchaseRefundStatus.FAILED) {
            if (request.getStatus()!=target || !Objects.equals(request.getCompletedAt(),completed) || !Objects.equals(request.getFailureCode(),code))
                messages.review(envelope,"CONFLICTING_TERMINAL_REFUND");
            return;
        }
        if (!reference.equals(purchase.getRefundReference()) ||
                purchase.getRefundId()!=null && !refundId.equals(purchase.getRefundId())) {
            messages.review(envelope,"STALE_REFUND_RESULT");return;
        }
        guard(userId);
        SubscriptionPeriod period=period(purchase);
        if (period==null) {
            messages.review(envelope,"REFUND_PERIOD_REQUIRES_RECONCILIATION");
            purchase.setReviewReason("REFUND_PERIOD_REQUIRES_RECONCILIATION");
            if (purchase.getPeriodId()==null) {purchase.setStatus(PurchaseStatus.REQUIRES_REVIEW);purchase.setBlocksNewPurchase(true);}
            purchases.save(purchase);return;
        }
        request.setRefundId(refundId);request.setStatus(target);request.setCompletedAt(completed);request.setFailureCode(code);
        refunds.save(request);
        purchase.setRefundId(refundId);purchase.setRefundStatus(target);
        if (success) {
            subscriptions.refundPeriod(period.getId());purchase.setStatus(PurchaseStatus.REFUNDED);
        } else purchase.setStatus(PurchaseStatus.ACTIVE);
        purchases.save(purchase);
        if (success) entitlements.refreshOwned(userId,now());
    }
    private void guard(String userId) {
        SubscriptionAccount account=subscriptions.findAccount(userId);
        if (account==null || !purchases.guardAccount(account)) throw new OptimisticLockingFailureException("Account changed");
    }
    private SubscriptionPeriod period(PremiumPurchase purchase) {
        if (purchase.getPaymentId()==null || purchase.getPeriodId()==null) return null;
        return subscriptions.findPeriodsNewestFirst(purchase.getUserId()).stream()
                .filter(p->purchase.getPeriodId().equals(p.getId()) && purchase.getPaymentId().equals(p.getPaymentId()) && p.getKind()!=PeriodKind.TRIAL)
                .findFirst().orElse(null);
    }
    private RefundResponse response(SubscriptionRefundRequest request) {
        return new RefundResponse(request.getRefundReference(),request.getPurchaseReference(),request.getRefundId(),
                request.getStatus().name(),request.getRequestedAt(),request.getCompletedAt(),request.getFailureCode());
    }
    private Instant now() { return Instant.ofEpochMilli(clock.millis()); }
    private SubscriptionRequestException error(int status,String code,String message) {return new SubscriptionRequestException(status,code,message);}
    private boolean retryable(Throwable failure) {
        for (Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if (cause instanceof DuplicateKeyException || cause instanceof OptimisticLockingFailureException) return true;
            if (cause instanceof com.mongodb.MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode()==112)) return true;
        }
        return false;
    }
}
