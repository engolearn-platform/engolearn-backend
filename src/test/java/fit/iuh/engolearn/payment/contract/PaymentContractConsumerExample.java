package fit.iuh.engolearn.payment.contract;

import java.time.Instant;
import java.util.function.Consumer;

final class PaymentContractConsumerExample {

    static void exchangeFacts(
            Consumer<PaymentSucceeded> successConsumer,
            Consumer<PaymentRefundSucceeded> refundConsumer) {
        successConsumer.accept(new PaymentSucceeded(
                "6af206dd-aa35-499c-8737-ec868e434ea6",
                "example-payment",
                "example-purchase-reference",
                "example-user",
                12345L,
                "VND",
                Instant.parse("2026-10-29T00:00:00Z")));
        refundConsumer.accept(new PaymentRefundSucceeded(
                "3ab0d158-ea32-41bb-a7bf-24ce8eeccf63",
                "example-refund",
                "example-payment",
                "example-purchase-reference",
                "example-user",
                Instant.parse("2026-10-29T01:00:00Z")));
    }
}
