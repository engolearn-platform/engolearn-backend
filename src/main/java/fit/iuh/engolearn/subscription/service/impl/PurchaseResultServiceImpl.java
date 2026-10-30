package fit.iuh.engolearn.subscription.service.impl;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.subscription.model.PremiumPurchase;
import fit.iuh.engolearn.subscription.model.enums.PurchaseStatus;
import fit.iuh.engolearn.subscription.repository.*;
import fit.iuh.engolearn.subscription.service.PurchaseResultService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
@Service @RequiredArgsConstructor
public class PurchaseResultServiceImpl implements PurchaseResultService {
    private final PurchaseRepository purchases;
    private final SubscriptionMessageRepository reviews;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final fit.iuh.engolearn.subscription.service.PaidActivationService activation;
    public void apply(MessageEnvelopeDto envelope) {
        PremiumPurchase purchase = purchases.findByReference(envelope.aggregateId());
        if (purchase == null) { reviews.review(envelope, "PURCHASE_NOT_FOUND"); return; }
        switch (envelope.messageType()) {
            case "PAYMENT_SUCCEEDED" -> activation.apply(mapper.readValue(envelope.payload(),PaymentSucceededEvent.class),envelope);
            case "CHECKOUT_READY" -> {
                var event = mapper.readValue(envelope.payload(), CheckoutReadyEvent.class);
                if (!valid(envelope, event.eventId(), event.purchaseReference()) ||
                        purchase.getPaymentId() != null && !purchase.getPaymentId().equals(event.paymentId()) ||
                        !purchase.getExpiresAt().equals(event.expiresAt())) { review(purchase, envelope); return; }
                if (purchase.getStatus() != PurchaseStatus.PAYMENT_SETUP_PENDING) return;
                purchase.setPaymentId(event.paymentId()); purchase.setCheckoutUrl(event.checkoutUrl());
                purchase.setStatus(PurchaseStatus.PAYMENT_PENDING); purchases.save(purchase);
            }
            case "CHECKOUT_REJECTED" -> {
                var event = mapper.readValue(envelope.payload(), CheckoutRejectedEvent.class);
                if (!valid(envelope, event.eventId(), event.purchaseReference())) { review(purchase, envelope); return; }
                if (purchase.getStatus() == PurchaseStatus.PAYMENT_SETUP_PENDING)
                    finish(purchase, PurchaseStatus.PAYMENT_SETUP_FAILED, event.reasonCode());
            }
            case "PAYMENT_FAILED" -> {
                var event = mapper.readValue(envelope.payload(), PaymentFailedEvent.class);
                if (!correlates(purchase, envelope, event.eventId(), event.purchaseReference(), event.paymentId(), event.userId())) {
                    review(purchase, envelope); return;
                }
                if (waiting(purchase)) { purchase.setPaymentId(event.paymentId()); finish(purchase, PurchaseStatus.PAYMENT_FAILED, event.failureCode()); }
            }
            case "PAYMENT_EXPIRED" -> {
                var event = mapper.readValue(envelope.payload(), PaymentExpiredEvent.class);
                if (!correlates(purchase, envelope, event.eventId(), event.purchaseReference(), event.paymentId(), event.userId())) {
                    review(purchase, envelope); return;
                }
                if (event.expiredAt().isBefore(purchase.getExpiresAt()) || clock.instant().isBefore(event.expiredAt())) {
                    review(purchase, envelope); return;
                }
                if (waiting(purchase)) { purchase.setPaymentId(event.paymentId()); finish(purchase, PurchaseStatus.PAYMENT_EXPIRED, "CHECKOUT_EXPIRED"); }
            }
            default -> throw new IllegalArgumentException("Unsupported Payment result");
        }
    }
    private boolean valid(MessageEnvelopeDto envelope, String id, String reference) {
        return envelope.messageId().equals(id) && envelope.aggregateId().equals(reference);
    }
    private boolean correlates(PremiumPurchase p, MessageEnvelopeDto e, String id, String ref, String payment, String user) {
        return valid(e,id,ref) && p.getUserId().equals(user) && (p.getPaymentId() == null || p.getPaymentId().equals(payment));
    }
    private boolean waiting(PremiumPurchase p) { return p.getStatus() == PurchaseStatus.PAYMENT_SETUP_PENDING || p.getStatus() == PurchaseStatus.PAYMENT_PENDING; }
    private void finish(PremiumPurchase p, PurchaseStatus status, String reason) {
        p.setStatus(status); p.setFailureCode(reason); p.setBlocksNewPurchase(false); purchases.save(p);
    }
    private void review(PremiumPurchase p, MessageEnvelopeDto envelope) {
        reviews.review(envelope, "PAYMENT_CORRELATION_CONFLICT"); p.setReviewReason("PAYMENT_CORRELATION_CONFLICT");
        if (waiting(p)) { p.setStatus(PurchaseStatus.REQUIRES_REVIEW); p.setBlocksNewPurchase(true); }
        purchases.save(p);
    }
}
