package fit.iuh.engolearn.shared.messaging;

public interface MessageConsumer {
    String recipient();
    void consume(MessageEnvelopeDto envelope);
}
