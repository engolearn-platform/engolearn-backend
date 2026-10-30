package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class SubscriptionHttpValidation {
    private SubscriptionHttpValidation() {}

    static SubscriptionRequestException invalidAdmin() {
        return new SubscriptionRequestException(400, "INVALID_REQUEST", "Request fields or values are invalid");
    }

    static SubscriptionRequestException invalidTrial() {
        return new SubscriptionRequestException(400, "INVALID_REQUEST",
                "An empty JSON object and a UUID Idempotency-Key are required");
    }

    static String scope(String value) {
        if (!Set.of("FREE", "TRIAL", "PREMIUM").contains(value)) throw invalidAdmin();
        return value;
    }

    static long positive(String value) {
        try {
            long number = Long.parseLong(value);
            if (number < 1 || number > 9_007_199_254_740_991L) throw invalidAdmin();
            return number;
        } catch (NumberFormatException ex) {
            throw invalidAdmin();
        }
    }

    private static boolean uuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static String adminKey(String value) {
        if (!uuid(value)) throw invalidAdmin();
        return value.toLowerCase(Locale.ROOT);
    }

    static String trialKey(String value) {
        if (!uuid(value)) throw invalidTrial();
        return value;
    }
}
