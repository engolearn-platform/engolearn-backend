package fit.iuh.engolearn.shared.messaging;

import java.time.Instant;

public final class MessageValidation {
    private MessageValidation() {}
    public static void text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required message value is blank");
    }
    public static void metadata(String id, int version, Instant at) {
        text(id);
        if (version != 1 || at == null) throw new IllegalArgumentException("Unsupported or incomplete message metadata");
    }
    public static void money(long amountVnd, String currency) {
        if (amountVnd < 0 || amountVnd > 9_007_199_254_740_991L || !"VND".equals(currency))
            throw new IllegalArgumentException("Expected integer VND amount");
    }
}
