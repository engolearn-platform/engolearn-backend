package fit.iuh.engolearn.companion.service;

import java.util.Objects;

/** App-produced JSON result correlated to exactly one previously returned tool call. */
public record CompanionToolResult(String callId, String name, String json) {
    public CompanionToolResult {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("Tool result call ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool result name is required");
        }
        Objects.requireNonNull(json, "json");
    }

    @Override
    public String toString() {
        return "CompanionToolResult[callId=" + callId + ", name=" + name + ", json=redacted]";
    }
}
