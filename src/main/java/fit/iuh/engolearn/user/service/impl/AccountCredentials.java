package fit.iuh.engolearn.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class AccountCredentials {
    private AccountCredentials() {}
    static void validatePassword(String password) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < 8 || bytes > 72)
            throw new AuthFailure("Password must contain at least 8 characters and fit the supported password limit", "VALIDATION_ERROR", 400);
    }
    static String canonicalEmail(String email) {
        if (email == null || email.trim().length() > 254 || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new AuthFailure("A valid email is required", "VALIDATION_ERROR", 400);
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
