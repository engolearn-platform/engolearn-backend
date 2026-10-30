package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.payment.contract.event.PaymentSucceededEvent;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
public interface PaidActivationService { void apply(PaymentSucceededEvent event,MessageEnvelopeDto envelope); }
