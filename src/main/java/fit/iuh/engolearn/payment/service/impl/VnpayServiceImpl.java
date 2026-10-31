package fit.iuh.engolearn.payment.service.impl;

import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.payment.dto.response.*;
import fit.iuh.engolearn.payment.infra.vnpay.VnpaySettings;
import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.payment.model.enums.*;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.*;
import fit.iuh.engolearn.payment.utils.VnpayProtocol;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class VnpayServiceImpl implements VnpayService {
    private final VnpaySettings settings;
    private final ProviderWorkRepository work;
    private final PaymentTransactionRepository payments;
    private final RefundTransactionRepository refunds;
    private final PaymentMessageRepository messages;
    private final MongoMessageTransactions transactions;
    private final CheckoutService checkout;
    private final PaymentOutcomeService outcomes;
    private final RefundService refundOutcomes;
    private final VnpayGateway gateway;
    private final ObjectMapper mapper;
    private final Clock clock;

    public VnpayIpnResponse ipn(Map<String,List<String>> parameters) {
        if (!settings.isEnabled()) return ack("99","VNPay is disabled");
        try {
            settings.validate();Map<String,String> fields=single(parameters);
            if (!VnpayProtocol.verifyQuery(settings.getHashSecret(),fields) || !settings.getTmnCode().equals(fields.get("vnp_TmnCode")))
                return ack("97","Invalid signature");
            return transactions.independent(()->{
                PaymentTransaction payment=work.findPayment(fields.get("vnp_TxnRef"));
                if (payment==null) return ack("01","Order not found");
                if (VnpayProtocol.vnd(fields.get("vnp_Amount"))!=payment.getAmountVnd()) {
                    reviewPayment(payment,"VNPAY_AMOUNT_MISMATCH",fields);return ack("04","Invalid amount");
                }
                if (!merchant(payment) || payment.getProviderCreatedAt()==null) return ack("99","Merchant mismatch");
                payments.guardRefund(payment.getId());
                boolean wasTerminal=payment.getStatus()==PaymentStatus.SUCCEEDED || payment.getStatus()==PaymentStatus.FAILED || payment.getStatus()==PaymentStatus.EXPIRED;
                if (!applyPayment(payment,fields,false)) return ack("99","Outcome requires reconciliation");
                return ack(wasTerminal?"02":"00",wasTerminal?"Order already confirmed":"Confirm success");
            });
        } catch (RuntimeException rejected) {return ack("99","Unable to confirm outcome");}
    }
    public VnpayReturnResponse returned(Map<String,List<String>> parameters) {
        try {
            if (!settings.isEnabled()) return new VnpayReturnResponse(false,null,"VNPay is disabled");
            settings.validate();Map<String,String> fields=single(parameters);
            boolean valid=VnpayProtocol.verifyQuery(settings.getHashSecret(),fields) && settings.getTmnCode().equals(fields.get("vnp_TmnCode"));
            PaymentTransaction payment=valid?work.findPayment(fields.get("vnp_TxnRef")):null;
            return new VnpayReturnResponse(valid,payment==null?null:payment.getPurchaseReference(),
                    valid?"Check authenticated purchase detail for the confirmed result":"Invalid signature");
        } catch (RuntimeException rejected) {return new VnpayReturnResponse(false,null,"Unable to verify return");}
    }
    public void process() {
        if (!settings.isEnabled()) return;
        settings.validate();
        for (int i=0;i<settings.getBatchSize();i++) {
            PaymentTransaction claim=work.claimPayment(now(),now().plus(settings.getLease()),now().plus(settings.getQueryInterval()));
            if (claim==null) break;
            try {
                if (claim.getStatus()==PaymentStatus.CHECKOUT_SETUP_PENDING) prepare(claim.getId());
                else reconcilePayment(claim);
            } catch (RuntimeException uncertain) {
                if (payments.findById(claim.getId()).getStatus()!=PaymentStatus.CHECKOUT_SETUP_PENDING) outcomes.markUnknown(claim.getId());
            }
            finally {work.releasePayment(claim,now().plus(settings.getQueryInterval()));}
        }
        for (int i=0;i<settings.getBatchSize();i++) {
            RefundTransaction claim=work.claimRefund(now(),now().plus(settings.getLease()),now().plus(settings.getQueryInterval()));
            if (claim==null) break;
            try {processRefund(claim);}
            catch (RuntimeException uncertain) {refundOutcomes.markUnknown(claim.getId());}
            finally {work.releaseRefund(claim,now().plus(settings.getQueryInterval()));}
        }
    }
    private void prepare(String id) {
        transactions.independent(()->{
            PaymentTransaction payment=payments.findById(id);
            if (payment.getStatus()!=PaymentStatus.CHECKOUT_SETUP_PENDING) return null;
            payments.guardRefund(id);
            if (!clock.instant().isBefore(payment.getExpiresAt())) {outcomes.confirmUnpaidExpiry(id);return null;}
            if (payment.getClientIp()==null || !payment.getClientIp().matches("[0-9a-fA-F:.]{2,45}")) {
                reject(payment,"CLIENT_IP_REQUIRED");return null;
            }
            try {VnpayProtocol.amount(payment.getAmountVnd());}
            catch (IllegalArgumentException unsupported) {reject(payment,"VNPAY_AMOUNT_UNSUPPORTED");return null;}
            payment.setProvider("VNPAY");payment.setProviderTmnCode(settings.getTmnCode());
            payment.setProviderReference(VnpayProtocol.attempt("pay:"+id));payment.setProviderAttemptId(payment.getProviderReference());
            payment.setProviderCreatedAt(now().truncatedTo(ChronoUnit.SECONDS));
            Map<String,String> fields=base("pay");
            fields.put("vnp_TxnRef",payment.getProviderReference());fields.put("vnp_Amount",VnpayProtocol.amount(payment.getAmountVnd()));
            fields.put("vnp_CreateDate",VnpayProtocol.date(payment.getProviderCreatedAt()));fields.put("vnp_ExpireDate",VnpayProtocol.date(payment.getExpiresAt()));
            fields.put("vnp_CurrCode","VND");fields.put("vnp_IpAddr",payment.getClientIp());fields.put("vnp_Locale","vn");
            fields.put("vnp_OrderInfo","Premium purchase "+payment.getProviderReference());fields.put("vnp_OrderType","other");
            fields.put("vnp_ReturnUrl",settings.getReturnUrl());
            String data=VnpayProtocol.query(fields);
            payments.save(payment);
            checkout.prepared(id,settings.getPayUrl()+"?"+data+"&vnp_SecureHash="+VnpayProtocol.hash(settings.getHashSecret(),data),payment.getExpiresAt());
            return null;
        });
    }
    private void reject(PaymentTransaction payment,String code) {
        payment.setStatus(PaymentStatus.CHECKOUT_SETUP_FAILED);payments.save(payment);
        String id=UUID.randomUUID().toString();Instant now=now();
        var event=new CheckoutRejectedEvent(id,1,now,payment.getPurchaseReference(),code,now);
        messages.append(new MessageEnvelopeDto(id,"CHECKOUT_REJECTED",1,payment.getPurchaseReference(),"subscription.payment",now,mapper.writeValueAsString(event)));
    }
    private void reconcilePayment(PaymentTransaction payment) {
        if (!merchant(payment)) {hold(payment.getId(),"VNPAY_MERCHANT_MISMATCH");return;}
        Map<String,String> response=gateway.post(query(payment,payment.getProviderTransactionNo()));
        if (!api(response,false,payment) || !"00".equals(response.get("vnp_ResponseCode")) || !"01".equals(response.get("vnp_TransactionType"))) {
            outcomes.markUnknown(payment.getId());return;
        }
        transactions.independent(()->{
            payments.guardRefund(payment.getId());
            if (!applyPayment(payments.findById(payment.getId()),response,true)) outcomes.markUnknown(payment.getId());
            return null;
        });
    }
    private boolean applyPayment(PaymentTransaction payment,Map<String,String> fields,boolean queried) {
        if (!merchant(payment) || VnpayProtocol.vnd(fields.get("vnp_Amount"))!=payment.getAmountVnd()) return false;
        String status=fields.get("vnp_TransactionStatus"),code=fields.get("vnp_ResponseCode"),number=fields.get("vnp_TransactionNo");
        if (number==null || !number.matches("[0-9]{1,15}") ||
                payment.getProviderTransactionNo()!=null && !number.equals(payment.getProviderTransactionNo())) {
            reviewPayment(payment,"VNPAY_TRANSACTION_MISMATCH",fields);return false;
        }
        boolean succeeded="00".equals(status) && "00".equals(code);
        boolean failed="02".equals(status) && (queried || Set.of("09","10","11","12","13","24","51","65","75","79").contains(code));
        if (!succeeded && !failed) {outcomes.markUnknown(payment.getId());return false;}
        if (payment.getStatus()==PaymentStatus.SUCCEEDED && !succeeded ||
                (payment.getStatus()==PaymentStatus.FAILED || payment.getStatus()==PaymentStatus.EXPIRED) && succeeded) {
            hold(payment.getId(),"VNPAY_CONFLICTING_OUTCOME");return false;
        }
        Instant paid=succeeded?VnpayProtocol.instant(fields.get("vnp_PayDate")):null;
        if (succeeded && (paid.isAfter(clock.instant()) || paid.isBefore(payment.getProviderCreatedAt()) ||
                payment.getPaidAt()!=null && !paid.equals(payment.getPaidAt()))) return false;
        payment.setProviderTransactionNo(number);payments.save(payment);
        if (succeeded) outcomes.confirmSuccess(payment.getId(),payment.getAmountVnd(),"VND",paid);
        else outcomes.confirmFailure(payment.getId(),queried?"VNPAY_STATUS_02":"VNPAY_"+code);
        PaymentStatus result=payments.findById(payment.getId()).getStatus();
        return succeeded?result==PaymentStatus.SUCCEEDED:result==PaymentStatus.FAILED || result==PaymentStatus.EXPIRED;
    }
    private void processRefund(RefundTransaction claim) {
        PaymentTransaction payment=payments.findById(claim.getPaymentId());
        if (payment==null || !merchant(payment) || payment.getProviderTransactionNo()==null || payment.getReviewReason()!=null) {
            transactions.independent(()->{RefundTransaction refund=refunds.findById(claim.getId());
                refund.setReviewReason("VNPAY_ORIGINAL_PAYMENT_REQUIRES_REVIEW");refunds.save(refund);return null;});
            refundOutcomes.markUnknown(claim.getId());return;
        }
        boolean execute=claim.getStatus()==RefundStatus.REQUESTED;
        if (execute) {
            transactions.independent(()->{
                payments.guardRefund(payment.getId());RefundTransaction refund=refunds.findById(claim.getId());
                if (refund.getProviderCreatedAt()==null) {
                    refund.setProviderCreatedAt(now().truncatedTo(ChronoUnit.SECONDS));refund.setProviderTmnCode(settings.getTmnCode());
                    refund.setProviderAttemptId(VnpayProtocol.attempt("refund:"+refund.getRefundReference()));refunds.save(refund);
                }
                return null;
            });
            execute=refundOutcomes.startProcessing(claim.getId());
        }
        RefundTransaction refund=refunds.findById(claim.getId());
        if (refund.getProviderCreatedAt()==null || !settings.getTmnCode().equals(refund.getProviderTmnCode())) {
            refundOutcomes.markUnknown(refund.getId());return;
        }
        Map<String,String> response=gateway.post(execute?refundRequest(payment,refund):query(payment,refund.getProviderTransactionNo()));
        if (!api(response,execute,payment) || !"02".equals(response.get("vnp_TransactionType"))) {
            refundOutcomes.markUnknown(refund.getId());return;
        }
        if (!execute && refund.getProviderTransactionNo()==null) {
            transactions.independent(()->{
                RefundTransaction current=refunds.findById(refund.getId());
                current.setReviewReason("VNPAY_REFUND_ID_REQUIRES_RECONCILIATION");refunds.save(current);
                messages.review(new MessageEnvelopeDto(UUID.randomUUID().toString(),"VNPAY_REFUND_REVIEW",1,current.getRefundReference(),
                        "payment.vnpay.review",now(),mapper.writeValueAsString(response)),"VNPAY_REFUND_ID_REQUIRES_RECONCILIATION");
                return null;
            });
            refundOutcomes.markUnknown(refund.getId());return;
        }
        transactions.independent(()->{
            payments.guardRefund(payment.getId());RefundTransaction current=refunds.findById(refund.getId());
            String number=response.get("vnp_TransactionNo"),status=response.get("vnp_TransactionStatus"),code=response.get("vnp_ResponseCode");
            if (number==null || !number.matches("[0-9]{1,15}") ||
                    current.getProviderTransactionNo()!=null && !current.getProviderTransactionNo().equals(number)) {
                refundOutcomes.markUnknown(current.getId());return null;
            }
            current.setProviderTransactionNo(number);refunds.save(current);
            if ("00".equals(code) && "00".equals(status)) {
                Instant refunded=VnpayProtocol.instant(response.get("vnp_PayDate"));
                refundOutcomes.confirmSuccess(current.getId(),current.getAmountVnd(),current.getCurrency(),refunded);
            } else if ("00".equals(code) && "09".equals(status))
                refundOutcomes.confirmFailure(current.getId(),"VNPAY_REFUND_REJECTED",now());
            else refundOutcomes.markUnknown(current.getId());
            return null;
        });
    }
    private Map<String,String> refundRequest(PaymentTransaction payment,RefundTransaction refund) {
        Map<String,String> request=base("refund");request.put("vnp_RequestId",refund.getProviderAttemptId());
        request.put("vnp_TransactionType","02");request.put("vnp_TxnRef",payment.getProviderReference());
        request.put("vnp_Amount",VnpayProtocol.amount(refund.getAmountVnd()));request.put("vnp_TransactionNo",payment.getProviderTransactionNo());
        request.put("vnp_TransactionDate",VnpayProtocol.date(payment.getProviderCreatedAt()));request.put("vnp_CreateBy",refund.getRequestedBy());
        request.put("vnp_CreateDate",VnpayProtocol.date(refund.getProviderCreatedAt()));request.put("vnp_IpAddr",settings.getServerIp());
        request.put("vnp_OrderInfo","Full refund "+refund.getProviderAttemptId());
        request.put("vnp_SecureHash",VnpayProtocol.hash(settings.getHashSecret(),VnpayProtocol.pipe(request,
                "RequestId","Version","Command","TmnCode","TransactionType","TxnRef","Amount","TransactionNo","TransactionDate","CreateBy","CreateDate","IpAddr","OrderInfo")));
        return request;
    }
    private Map<String,String> query(PaymentTransaction payment,String transactionNo) {
        Map<String,String> request=base("querydr");request.put("vnp_RequestId",UUID.randomUUID().toString().replace("-",""));
        request.put("vnp_TxnRef",payment.getProviderReference());request.put("vnp_TransactionDate",VnpayProtocol.date(payment.getProviderCreatedAt()));
        request.put("vnp_CreateDate",VnpayProtocol.date(now()));request.put("vnp_IpAddr",settings.getServerIp());
        request.put("vnp_OrderInfo","Query transaction "+payment.getProviderReference());
        if (transactionNo!=null) request.put("vnp_TransactionNo",transactionNo);
        request.put("vnp_SecureHash",VnpayProtocol.hash(settings.getHashSecret(),VnpayProtocol.pipe(request,
                "RequestId","Version","Command","TmnCode","TxnRef","TransactionDate","CreateDate","IpAddr","OrderInfo")));
        return request;
    }
    private boolean api(Map<String,String> fields,boolean refund,PaymentTransaction payment) {
        return VnpayProtocol.verifyApi(settings.getHashSecret(),fields,refund) &&
                settings.getTmnCode().equals(fields.get("vnp_TmnCode")) && (refund?"refund":"querydr").equals(fields.get("vnp_Command")) &&
                payment.getProviderReference().equals(fields.get("vnp_TxnRef")) && VnpayProtocol.vnd(fields.get("vnp_Amount"))==payment.getAmountVnd();
    }
    private boolean merchant(PaymentTransaction payment) {
        return "VNPAY".equals(payment.getProvider()) && settings.getTmnCode().equals(payment.getProviderTmnCode()) &&
                payment.getProviderReference()!=null && payment.getProviderCreatedAt()!=null;
    }
    private Map<String,String> base(String command) {
        Map<String,String> fields=new LinkedHashMap<>();fields.put("vnp_Version","2.1.0");fields.put("vnp_Command",command);
        fields.put("vnp_TmnCode",settings.getTmnCode());return fields;
    }
    private Map<String,String> single(Map<String,List<String>> parameters) {
        Map<String,String> fields=new TreeMap<>();
        parameters.forEach((name,values)->{
            if (name.startsWith("vnp_")) {
                if (values==null || values.size()!=1 || values.getFirst()==null || name.length()>64 || values.getFirst().length()>2048)
                    throw new IllegalArgumentException("Invalid VNPay parameter");
                fields.put(name,values.getFirst());
            }
        });
        if (fields.size()>40) throw new IllegalArgumentException("Too many VNPay parameters");return fields;
    }
    private void hold(String id,String reason) {
        transactions.required(()->{reviewPayment(payments.findById(id),reason,Map.of());return null;});
    }
    private void reviewPayment(PaymentTransaction payment,String reason,Map<String,String> fields) {
        payment.setReviewReason(reason);
        if (payment.getStatus()==PaymentStatus.CHECKOUT_SETUP_PENDING || payment.getStatus()==PaymentStatus.PENDING || payment.getStatus()==PaymentStatus.UNKNOWN)
            payment.setStatus(PaymentStatus.REVIEW_REQUIRED);
        payments.save(payment);
        messages.review(new MessageEnvelopeDto(UUID.randomUUID().toString(),"VNPAY_PROVIDER_REVIEW",1,payment.getPurchaseReference(),
                "payment.vnpay.review",now(),mapper.writeValueAsString(fields)),reason);
    }
    private VnpayIpnResponse ack(String code,String message) {return new VnpayIpnResponse(code,message);}
    private Instant now() {return Instant.ofEpochMilli(clock.millis());}
}
