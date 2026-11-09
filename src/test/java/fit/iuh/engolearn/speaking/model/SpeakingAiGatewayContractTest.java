package fit.iuh.engolearn.speaking.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingAiGatewayContractTest {
    @Test
    void requestCopiesNestedContextAndRequiresFinalizedTranscript() {
        var history = new ArrayList<>(List.of(
                new SpeakingAiRequestV1.HistoryTurn(SpeakingAiRequestV1.Role.LEARNER, "I want coffee.")));
        var goals = new ArrayList<>(List.of(
                new SpeakingAiRequestV1.PinnedGoal("coffee", "Coffee", "order coffee", SpeakingGoal.Priority.REQUIRED)));
        var snapshot = new SpeakingAiRequestV1.PinnedSnapshot("item", "version", "Cafe", "Order a drink",
                goals, List.of("coffee"), List.of("Could I have"));

        var request = new SpeakingAiRequestV1("speaking.ai.draft.v1", "speaking.ai.prompt.v2", snapshot,
                new ArrayList<>(List.of("coffee")), history, List.of(), "I want coffee.");

        history.clear();
        goals.clear();

        assertEquals(1, request.recentHistory().size());
        assertEquals(1, request.pinnedSnapshot().goals().size());
        assertThrows(IllegalArgumentException.class, () -> new SpeakingAiRequestV1(
                "speaking.ai.draft.v1", "speaking.ai.prompt.v2", snapshot, List.of(), List.of(), List.of(), " "));
        assertThrows(NullPointerException.class, () -> new SpeakingAiRequestV1(
                "speaking.ai.draft.v1", "speaking.ai.prompt.v2", null, List.of(), List.of(), List.of(), "final"));
    }

    @Test
    void successKeepsRawCandidateAndFailuresAreTyped() {
        var metadata = new ProviderRawDraft.ProviderMetadata("gemini-test", 200, null, null,
                null, null, null);
        var success = new GatewayOutcome.Success(new ProviderRawDraft("{\"contractVersion\":\"speaking.ai.draft.v1\"}", metadata));
        var failure = new GatewayOutcome.HttpFailure(new GatewayOutcome.ProviderFailure(
                GatewayOutcome.FailureCode.RATE_LIMITED_429, "rate limited", 429, metadata, null));

        assertEquals("{\"contractVersion\":\"speaking.ai.draft.v1\"}", success.draft().candidateJson());
        assertEquals(GatewayOutcome.FailureCode.RATE_LIMITED_429, failure.failure().code());
        assertInstanceOf(GatewayOutcome.Success.class, success);
    }
}
