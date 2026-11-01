package fit.iuh.engolearn.payment.service.impl;
import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.payment.contract.event.*;
import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.payment.model.enums.PaymentStatus;
import fit.iuh.engolearn.payment.repository.*;
import fit.iuh.engolearn.payment.service.CheckoutService;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
@Service @RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final PaymentTransactionRepository payments;
    private final PaymentMessageRepository messages;
    private final MongoMessageTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    public void accept(CreateCheckoutCommandDto command, MessageEnvelopeDto envelope) {
        PaymentTransaction existing = payments.findByPurchase(command.purchaseReference());
        if (existing != null) {
            if (!existing.getUserId().equals(command.userId()) || existing.getAmountVnd() != command.amountVnd() ||
                    !existing.getCurrency().equals(command.currency()) || !existing.getExpiresAt().equals(command.expiresAt()) ||
                    !java.util.Objects.equals(existing.getClientIp(),command.clientIp()))
                messages.review(envelope, "CHECKOUT_PAYLOAD_CONFLICT");
            return;
        }
        PaymentTransaction payment = PaymentTransaction.builder().id(UUID.randomUUID().toString())
                .purchaseReference(command.purchaseReference()).userId(command.userId()).amountVnd(command.amountVnd())
                .currency(command.currency()).expiresAt(command.expiresAt()).clientIp(command.clientIp()).intentCreatedAt(command.occurredAt()).status(PaymentStatus.CHECKOUT_SETUP_PENDING).build();
        payments.insert(payment);
        if (!clock.instant().isBefore(command.expiresAt())) {
            payment.setStatus(PaymentStatus.EXPIRED);
            payments.save(payment);
            Instant now = Instant.ofEpochMilli(clock.millis());
            String id = UUID.randomUUID().toString();
            var event = new PaymentExpiredEvent(id, 1, now, payment.getId(), payment.getPurchaseReference(), payment.getUserId(), now);
            messages.append(new MessageEnvelopeDto(id, "PAYMENT_EXPIRED", 1, payment.getPurchaseReference(), "subscription.payment", now,
                    mapper.writeValueAsString(event)));
        }
    }
    public void prepared(String paymentId, String checkoutUrl, Instant expiresAt) {
        transactions.required(() -> {
            PaymentTransaction payment = payments.findById(paymentId);
            if (payment == null) throw new IllegalArgumentException("Payment not found");
            if (payment.getStatus() == PaymentStatus.PENDING && checkoutUrl.equals(payment.getCheckoutUrl()) &&
                    expiresAt.equals(payment.getExpiresAt())) return null;
            if (payment.getStatus() != PaymentStatus.CHECKOUT_SETUP_PENDING || !expiresAt.equals(payment.getExpiresAt()) ||
                    !clock.instant().isBefore(expiresAt)) throw new IllegalStateException("Checkout cannot be prepared");
            java.net.URI url = java.net.URI.create(checkoutUrl);
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null)
                throw new IllegalArgumentException("An HTTPS checkout URL is required");
            payment.setStatus(PaymentStatus.PENDING); payment.setCheckoutUrl(checkoutUrl); payments.save(payment);
            String id = UUID.randomUUID().toString(); Instant now = Instant.ofEpochMilli(clock.millis());
            var event = new CheckoutReadyEvent(id, 1, now, payment.getPurchaseReference(), paymentId, checkoutUrl, expiresAt);
            messages.append(new MessageEnvelopeDto(id, "CHECKOUT_READY", 1, payment.getPurchaseReference(), "subscription.payment", now,
                    mapper.writeValueAsString(event)));
            return null;
        });
    }
}
