package fit.iuh.engolearn.companion.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A requested call returned by the model. This value never executes the tool. */
public record CompanionToolCall(String callId, String name, Map<String, Object> arguments) {
    public CompanionToolCall {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("Tool call ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool call name is required");
        }
        arguments = arguments == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    @Override
    public String toString() {
        return "CompanionToolCall[callId=" + callId + ", name=" + name + ", arguments=redacted]";
    }
}
