package fit.iuh.engolearn.companion.service;

import java.util.Objects;

/** Provider-neutral conversation input. Tool exchanges are carried separately. */
public record CompanionPromptMessage(Role role, String text) {
    public CompanionPromptMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
    }

    public enum Role { SYSTEM, USER, ASSISTANT }

    @Override
    public String toString() {
        return "CompanionPromptMessage[role=" + role + ", text=redacted]";
    }
}
