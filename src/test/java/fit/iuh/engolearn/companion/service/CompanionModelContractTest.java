package fit.iuh.engolearn.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanionModelContractTest {
    @Test
    void emptyToolListExplicitlyDisablesToolsAndIsImmutable() {
        var request = new CompanionModelRequest(
                List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Hi")),
                List.of(), null, List.of());

        assertThat(request.tools()).isEmpty();
        assertThatThrownBy(() -> request.tools().add(
                new CompanionToolDefinition("lookup", "lookup", "{}")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolCallPreservesProviderIdAndNullableJsonArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("filter", null);
        var call = new CompanionToolCall("gemini-call-42", "getLatestAssessmentResult", arguments);

        arguments.put("mutatedAfterCreation", true);
        assertThat(call.callId()).isEqualTo("gemini-call-42");
        assertThat(call.arguments()).containsKey("filter").doesNotContainKey("mutatedAfterCreation");
        assertThat(call.arguments()).containsEntry("filter", null);
        assertThatThrownBy(() -> call.arguments().put("another", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolCallEventRetainsOpaqueContinuationWithoutLeakingIt() {
        CompanionModelContinuation continuation = new CompanionModelContinuation() {};
        var event = new CompanionModelEvent.ToolCalls(continuation, List.of(
                new CompanionToolCall("gemini-call-42", "getLatestAssessmentResult", Map.of())));

        assertThat(event.continuation()).isSameAs(continuation);
        assertThat(event.calls()).extracting(CompanionToolCall::callId).containsExactly("gemini-call-42");
    }

    @Test
    void toolResultsRequireContinuationAndKeepExactCallCorrelation() {
        var result = new CompanionToolResult("gemini-call-42", "getLatestAssessmentResult", "{\"found\":true}");
        CompanionModelContinuation continuation = new CompanionModelContinuation() {};

        assertThatThrownBy(() -> new CompanionModelRequest(List.of(), List.of(), null, List.of(result)))
                .isInstanceOf(IllegalArgumentException.class);
        var request = new CompanionModelRequest(List.of(), List.of(), continuation, List.of(result));
        assertThat(request.continuation()).isSameAs(continuation);
        assertThat(request.toolResults()).containsExactly(result);
    }

    @Test
    void usageCannotBeNegativeOrAbsentFromRoundCompletion() {
        assertThatThrownBy(() -> new CompanionUsage(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionModelEvent.RoundCompleted(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new CompanionModelEvent.RoundCompleted(new CompanionUsage(7)).usage().totalTokenCount())
                .isEqualTo(7);
    }
}
