package fit.iuh.engolearn.payment.service.impl;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.payment.model.PaymentTransaction;
import fit.iuh.engolearn.payment.model.enums.PaymentStatus;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.PaymentOutcomeService;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
@Service @RequiredArgsConstructor
public class PaymentOutcomeServiceImpl implements PaymentOutcomeService {
    private final PaymentTransactionRepository payments;
    private final PaymentMessageRepository messages;
    private final MongoMessageTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    public void confirmSuccess(String paymentId,long amountVnd,String currency,Instant paidAt) {
        MessageValidation.money(amountVnd,currency);
        if (paidAt == null) throw new IllegalArgumentException("Verified paidAt is required");
        Instant paid = Instant.ofEpochMilli(paidAt.toEpochMilli());
        transactions.required(() -> {
            PaymentTransaction payment = required(paymentId);
            if (payment.getAmountVnd() != amountVnd || !payment.getCurrency().equals(currency) || paid.isAfter(clock.instant())) {
                review(payment,"PAYMENT_SUCCESS_MISMATCH",Map.of("amountVnd",amountVnd,"currency",currency,"paidAt",paid)); return null;
            }
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                if (!paid.equals(payment.getPaidAt())) review(payment,"CONFLICTING_PAID_AT",Map.of("paidAt",paid));
                return null;
            }
            if (!unresolved(payment)) {
                review(payment,"SUCCESS_AFTER_TERMINAL_OUTCOME",Map.of("paidAt",paid)); return null;
            }
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setPaidAt(paid); payments.save(payment);
            String id=UUID.randomUUID().toString(); Instant now=now();
            append(payment,id,"PAYMENT_SUCCEEDED",now,new PaymentSucceededEvent(id,1,now,paymentId,
                    payment.getPurchaseReference(),payment.getUserId(),amountVnd,currency,paid));
            return null;
        });
    }
    public void confirmFailure(String paymentId,String failureCode) {
        MessageValidation.text(failureCode);
        transactions.required(() -> {
            PaymentTransaction payment=required(paymentId);
            if (!unresolved(payment)) return null;
            payment.setStatus(PaymentStatus.FAILED);payments.save(payment);
            String id=UUID.randomUUID().toString();Instant now=now();
            append(payment,id,"PAYMENT_FAILED",now,new PaymentFailedEvent(id,1,now,paymentId,payment.getPurchaseReference(),
                    payment.getUserId(),failureCode,now));return null;
        });
    }
    public void confirmUnpaidExpiry(String paymentId) {
        transactions.required(() -> {
            PaymentTransaction payment=required(paymentId);
            if (!unresolved(payment)) return null;
            if (clock.instant().isBefore(payment.getExpiresAt())) throw new IllegalStateException("Checkout has not expired");
            payment.setStatus(PaymentStatus.EXPIRED);payments.save(payment);
            String id=UUID.randomUUID().toString();Instant now=now();
            append(payment,id,"PAYMENT_EXPIRED",now,new PaymentExpiredEvent(id,1,now,paymentId,payment.getPurchaseReference(),
                    payment.getUserId(),now));return null;
        });
    }
    public void markUnknown(String paymentId) {
        transactions.required(() -> {
            PaymentTransaction payment=required(paymentId);
            if (unresolved(payment)) {payment.setStatus(PaymentStatus.UNKNOWN);payments.save(payment);}
            return null;
        });
    }
    private PaymentTransaction required(String id) {
        PaymentTransaction payment=payments.findById(id);
        if (payment==null) throw new IllegalArgumentException("Payment not found");return payment;
    }
    private boolean unresolved(PaymentTransaction payment) {
        return payment.getStatus()==PaymentStatus.PENDING || payment.getStatus()==PaymentStatus.UNKNOWN ||
                payment.getStatus()==PaymentStatus.CHECKOUT_SETUP_PENDING;
    }
    private Instant now() {return Instant.ofEpochMilli(clock.millis());}
    private void append(PaymentTransaction payment,String id,String type,Instant occurredAt,Object event) {
        messages.append(new MessageEnvelopeDto(id,type,1,payment.getPurchaseReference(),"subscription.payment",occurredAt,mapper.writeValueAsString(event)));
    }
    private void review(PaymentTransaction payment,String reason,Object evidence) {
        payment.setReviewReason(reason);
        if(payment.getStatus()!=PaymentStatus.SUCCEEDED) payment.setStatus(PaymentStatus.REVIEW_REQUIRED);
        payments.save(payment);
        messages.review(new MessageEnvelopeDto(UUID.randomUUID().toString(),"PAYMENT_OUTCOME_REVIEW",1,payment.getId(),
                "payment.outcome.review",now(),mapper.writeValueAsString(evidence)),reason);
    }
}
