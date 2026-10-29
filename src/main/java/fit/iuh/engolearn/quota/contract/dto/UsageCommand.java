package fit.iuh.engolearn.quota.contract.dto;

import java.time.Instant;

public record UsageCommand(String userId, String quotaType, long amount, String sourceFeature,
        String idempotencyKey, String sourceReference, Instant occurredAt) {
    public UsageCommand {
        requireIdentifier(userId);
        requireIdentifier(quotaType);
        requireIdentifier(sourceFeature);
        requireIdentifier(idempotencyKey);
        if (amount <= 0 || amount > 9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid amount");
        if (sourceReference != null && sourceReference.isBlank()) throw new IllegalArgumentException("Invalid reference");
    }

    private static void requireIdentifier(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier is required");
    }
}
