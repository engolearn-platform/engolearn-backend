package fit.iuh.engolearn.payment.service.impl;

import fit.iuh.engolearn.payment.contract.dto.FullRefundCommandDto;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.payment.model.enums.*;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.RefundService;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundTransactionRepository refunds;
    private final PaymentTransactionRepository payments;
    private final PaymentMessageRepository messages;
    private final MongoMessageTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public void accept(FullRefundCommandDto command,MessageEnvelopeDto envelope) {
        RefundTransaction previous=refunds.findByReference(command.refundReference());
        if (previous!=null) {
            if (!previous.getPaymentId().equals(command.paymentId()) || !previous.getPurchaseReference().equals(command.purchaseReference()) ||
                    !previous.getUserId().equals(command.userId()) || !previous.getRequestedBy().equals(command.requestedBy()) ||
                    !previous.getReason().equals(command.reason())) messages.review(envelope,"REFUND_PAYLOAD_CONFLICT");
            return;
        }
        PaymentTransaction payment=payments.findById(command.paymentId());
        if (payment==null || !payment.getPurchaseReference().equals(command.purchaseReference()) || !payment.getUserId().equals(command.userId()) ||
                command.occurredAt().isAfter(clock.instant()) || payment.getPaidAt()!=null && command.occurredAt().isBefore(payment.getPaidAt())) {
            messages.review(envelope,"REFUND_PAYMENT_MISMATCH");return;
        }
        payments.guardRefund(payment.getId());
        RefundTransaction blocking=refunds.findBlockingOrSucceeded(payment.getId());
        String rejection=payment.getStatus()!=PaymentStatus.SUCCEEDED?"PAYMENT_NOT_SUCCEEDED":
                blocking!=null?"REFUND_ALREADY_PENDING_OR_SUCCEEDED":null;
        RefundTransaction refund=RefundTransaction.builder().id(UUID.randomUUID().toString()).refundReference(command.refundReference())
                .paymentId(payment.getId()).purchaseReference(payment.getPurchaseReference()).userId(payment.getUserId())
                .amountVnd(payment.getAmountVnd()).currency(payment.getCurrency()).refundType(RefundType.FULL)
                .status(rejection==null?RefundStatus.REQUESTED:RefundStatus.FAILED).blocksNewRefund(rejection==null)
                .requestedBy(command.requestedBy()).reason(command.reason()).requestedAt(command.occurredAt())
                .providerAttemptId(command.refundReference()).failureCode(rejection).completedAt(rejection==null?null:now()).build();
        refunds.insert(refund);
        if (rejection==null && payment.getReviewReason()!=null) {
            refund.setStatus(RefundStatus.UNKNOWN);refund.setReviewReason("PAYMENT_REQUIRES_REVIEW");refunds.save(refund);
        }
        if (rejection!=null) append(refund,false,rejection,refund.getCompletedAt());
    }
    public boolean startProcessing(String refundId) {
        return transactions.independent(()->{
            RefundTransaction refund=required(refundId);payments.guardRefund(refund.getPaymentId());
            if (refund.getStatus()!=RefundStatus.REQUESTED) return false;
            if (payments.findById(refund.getPaymentId()).getReviewReason()!=null) {
                refund.setStatus(RefundStatus.UNKNOWN);refunds.save(refund);return false;
            }
            refund.setStatus(RefundStatus.PROCESSING);refunds.save(refund);return true;
        });
    }
    public void markUnknown(String refundId) {
        transactions.required(()->{
            RefundTransaction refund=required(refundId);payments.guardRefund(refund.getPaymentId());
            if (unresolved(refund)) {refund.setStatus(RefundStatus.UNKNOWN);refunds.save(refund);}return null;
        });
    }
    public void confirmSuccess(String refundId,long amountVnd,String currency,Instant refundedAt) {
        MessageValidation.money(amountVnd,currency);
        complete(refundId,true,amountVnd,currency,null,refundedAt);
    }
    public void confirmFailure(String refundId,String failureCode,Instant failedAt) {
        MessageValidation.text(failureCode);complete(refundId,false,0,null,failureCode,failedAt);
    }
    private void complete(String refundId,boolean success,long amount,String currency,String code,Instant completedAt) {
        if (completedAt==null) throw new IllegalArgumentException("Verified refund result time is required");
        Instant completed=Instant.ofEpochMilli(completedAt.toEpochMilli());
        transactions.required(()->{
            RefundTransaction refund=required(refundId);payments.guardRefund(refund.getPaymentId());
            PaymentTransaction payment=payments.findById(refund.getPaymentId());
            if (completed.isBefore(refund.getRequestedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)) || completed.isAfter(clock.instant()) ||
                    success && (payment==null || payment.getStatus()!=PaymentStatus.SUCCEEDED || amount!=refund.getAmountVnd() ||
                    !currency.equals(refund.getCurrency()) || amount!=payment.getAmountVnd() || !currency.equals(payment.getCurrency()))) {
                review(refund,"REFUND_OUTCOME_MISMATCH",success,amount,currency,code,completed);return null;
            }
            RefundStatus target=success?RefundStatus.SUCCEEDED:RefundStatus.FAILED;
            if (!unresolved(refund)) {
                if (refund.getStatus()!=target || !completed.equals(refund.getCompletedAt()) || !Objects.equals(code,refund.getFailureCode()))
                    review(refund,"CONFLICTING_TERMINAL_REFUND",success,amount,currency,code,completed);
                return null;
            }
            if (success) {
                RefundTransaction blocking=refunds.findBlockingOrSucceeded(refund.getPaymentId());
                if (blocking==null || !blocking.getId().equals(refund.getId())) {
                    review(refund,"REFUND_FINANCIAL_CONFLICT",true,amount,currency,code,completed);return null;
                }
                refund.setRefundedAt(completed);
            }
            refund.setStatus(target);refund.setBlocksNewRefund(success);refund.setFailureCode(code);refund.setCompletedAt(completed);
            refunds.save(refund);append(refund,success,code,completed);return null;
        });
    }
    private void append(RefundTransaction refund,boolean success,String code,Instant completed) {
        String id=UUID.randomUUID().toString();Instant now=now();
        Object event=success?new PaymentRefundSucceededEvent(id,1,now,refund.getRefundReference(),refund.getId(),refund.getPaymentId(),
                refund.getPurchaseReference(),refund.getUserId(),completed):new PaymentRefundFailedEvent(id,1,now,refund.getRefundReference(),
                refund.getId(),refund.getPaymentId(),refund.getPurchaseReference(),refund.getUserId(),code,completed);
        messages.append(new MessageEnvelopeDto(id,success?"PAYMENT_REFUND_SUCCEEDED":"PAYMENT_REFUND_FAILED",1,
                refund.getPurchaseReference(),"subscription.payment",now,mapper.writeValueAsString(event)));
    }
    private void review(RefundTransaction refund,String reason,boolean success,long amount,String currency,String code,Instant completed) {
        refund.setReviewReason(reason);
        if (unresolved(refund)) refund.setStatus(RefundStatus.UNKNOWN);
        refunds.save(refund);payments.markRefundReview(refund.getPaymentId(),reason);
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("refundId",refund.getId());evidence.put("success",success);evidence.put("amountVnd",amount);
        evidence.put("currency",currency);evidence.put("failureCode",code);evidence.put("completedAt",completed);
        messages.review(new MessageEnvelopeDto(UUID.randomUUID().toString(),"REFUND_OUTCOME_REVIEW",1,refund.getRefundReference(),
                "payment.refund.review",now(),mapper.writeValueAsString(evidence)),reason);
    }
    private RefundTransaction required(String id) {
        RefundTransaction refund=refunds.findById(id);
        if (refund==null) throw new IllegalArgumentException("Refund not found");return refund;
    }
    private boolean unresolved(RefundTransaction refund) {
        return refund.getStatus()==RefundStatus.REQUESTED || refund.getStatus()==RefundStatus.PROCESSING || refund.getStatus()==RefundStatus.UNKNOWN;
    }
    private Instant now() {return Instant.ofEpochMilli(clock.millis());}
}
