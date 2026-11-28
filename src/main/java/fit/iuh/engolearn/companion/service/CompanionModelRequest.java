package fit.iuh.engolearn.companion.service;

import java.util.List;
import java.util.Objects;

/** One provider inference round; an empty tool list explicitly disables tool calls. */
public record CompanionModelRequest(
        List<CompanionPromptMessage> messages,
        List<CompanionToolDefinition> tools,
        CompanionModelContinuation continuation,
        List<CompanionToolResult> toolResults) {
    public CompanionModelRequest {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        toolResults = List.copyOf(Objects.requireNonNull(toolResults, "toolResults"));
        if (continuation == null && !toolResults.isEmpty()) {
            throw new IllegalArgumentException("Tool results require their preceding model continuation");
        }
    }

    @Override
    public String toString() {
        return "CompanionModelRequest[messageCount=" + messages.size() + ", toolCount=" + tools.size()
                + ", hasContinuation=" + (continuation != null) + ", toolResultCount=" + toolResults.size() + "]";
    }
}
