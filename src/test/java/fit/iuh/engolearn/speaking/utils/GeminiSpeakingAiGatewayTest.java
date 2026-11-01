package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.GatewayOutcome;
import fit.iuh.engolearn.speaking.model.ProviderRawDraft;
import fit.iuh.engolearn.speaking.model.SpeakingAiRequestV1;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidationException;
import fit.iuh.engolearn.speaking.service.SpeakingDraftValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GeminiSpeakingAiGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void validCandidatePreservesRawJsonAndMakesExactlyOneRequest() {
        var calls = new AtomicInteger();
        var candidate = "{\"contractVersion\":\"speaking.ai.draft.v1\",\"raw\":true}";
        var gateway = gateway(request -> {
            calls.incrementAndGet();
            return response(200, wrapper(candidate), metadata(200));
        });

        var outcome = gateway.assess(request("I want coffee."));

        var success = assertInstanceOf(GatewayOutcome.Success.class, outcome);
        assertEquals(candidate, success.draft().candidateJson());
        assertEquals(1, calls.get());
    }

    @Test
    void requestContainsPromptV2Data() {
        var sent = new GeminiSpeakingAiGateway.ProviderHttpRequest[1];
        var gateway = gateway(request -> {
            sent[0] = request;
            return response(200, wrapper("{}"), metadata(200));
        });

        gateway.assess(request("I want the large coffee."));

        assertNotNull(sent[0]);
        assertTrue(sent[0].body().contains("PINNED_SNAPSHOT_DATA_BEGIN"));
        assertTrue(sent[0].body().contains("CURRENT_FINALIZED_LEARNER_TURN_DATA_BEGIN"));
        assertTrue(sent[0].body().contains("I want the large coffee."));
        assertTrue(sent[0].body().contains("LEARNER"));
        assertTrue(sent[0].body().contains("PARTNER"));
    }

    @Test
    void emptyCandidateIsDistinctAndDoesNotRetry() {
        var calls = new AtomicInteger();
        var gateway = gateway(request -> {
            calls.incrementAndGet();
            return response(200, "{\"candidates\":[]}", metadata(200));
        });

        var outcome = gateway.assess(request("I want coffee."));

        assertInstanceOf(GatewayOutcome.EmptyCandidate.class, outcome);
        assertEquals(1, calls.get());
    }

    @Test
    void malformedProviderWrapperIsTyped() {
        var gateway = gateway(request -> response(200, "{not-json", metadata(200)));

        var outcome = gateway.assess(request("I want coffee."));

        var malformed = assertInstanceOf(GatewayOutcome.MalformedProviderResponse.class, outcome);
        assertEquals(GatewayOutcome.FailureCode.MALFORMED_WRAPPER, malformed.failure().code());
    }

    @Test
    void malformedCandidatePathIsTyped() {
        var gateway = gateway(request -> response(200, "{\"candidates\":{}}", metadata(200)));

        var outcome = gateway.assess(request("I want coffee."));

        var malformed = assertInstanceOf(GatewayOutcome.MalformedProviderResponse.class, outcome);
        assertEquals(GatewayOutcome.FailureCode.MALFORMED_WRAPPER, malformed.failure().code());
    }

    @Test
    void malformedCandidateIsTypedWithoutWeakeningDownstreamValidation() {
        var gateway = gateway(request -> response(200, wrapper("not-json"), metadata(200)));

        var outcome = gateway.assess(request("I want coffee."));

        var malformed = assertInstanceOf(GatewayOutcome.MalformedProviderResponse.class, outcome);
        assertEquals(GatewayOutcome.FailureCode.MALFORMED_CANDIDATE, malformed.failure().code());
    }

    @Test
    void rateLimitAndServerFailuresAreClassifiedSeparately() {
        var rateLimited = gateway(request -> response(429, "{}", metadata(429))).assess(request("hello"));
        var serverError = gateway(request -> response(503, "{}", metadata(503))).assess(request("hello"));

        assertEquals(GatewayOutcome.FailureCode.RATE_LIMITED_429,
                assertInstanceOf(GatewayOutcome.HttpFailure.class, rateLimited).failure().code());
        assertEquals(GatewayOutcome.FailureCode.SERVER_5XX,
                assertInstanceOf(GatewayOutcome.HttpFailure.class, serverError).failure().code());
    }

    @Test
    void timeoutIsTyped() {
        var gateway = gateway(request -> { throw new HttpTimeoutException("timed out"); });

        var outcome = gateway.assess(request("hello"));

        assertEquals(GatewayOutcome.FailureCode.TIMEOUT,
                assertInstanceOf(GatewayOutcome.Timeout.class, outcome).failure().code());
    }

    @Test
    void interruptionRestoresInterruptFlagAndDoesNotRetry() {
        var calls = new AtomicInteger();
        var gateway = gateway(request -> {
            calls.incrementAndGet();
            throw new InterruptedException("cancelled");
        });

        var outcome = gateway.assess(request("hello"));

        assertEquals(GatewayOutcome.FailureCode.INTERRUPTED,
                assertInstanceOf(GatewayOutcome.Interrupted.class, outcome).failure().code());
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(1, calls.get());
    }

    @Test
    void messageLessInterruptionStillReturnsTypedOutcome() {
        var gateway = gateway(request -> { throw new InterruptedException(); });

        var outcome = gateway.assess(request("hello"));

        assertInstanceOf(GatewayOutcome.Interrupted.class, outcome);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void messageLessIoStillReturnsTypedOutcome() {
        var gateway = gateway(request -> { throw new IOException(); });

        var outcome = gateway.assess(request("hello"));

        assertEquals(GatewayOutcome.FailureCode.TRANSPORT,
                assertInstanceOf(GatewayOutcome.TransportFailure.class, outcome).failure().code());
    }

    @Test
    void missingProviderMetadataCannotBecomeSuccess() {
        var gateway = gateway(request -> response(200, wrapper("{}"), null));

        var outcome = gateway.assess(request("hello"));

        assertEquals(GatewayOutcome.FailureCode.MISSING_METADATA,
                assertInstanceOf(GatewayOutcome.MissingProviderMetadata.class, outcome).failure().code());
    }

    @Test
    void lifecycleFieldsRemainRawAndAreRejectedBySlice1Boundary() {
        var candidate = "{\"contractVersion\":\"speaking.ai.draft.v1\",\"assessment\":{\"turnValidityProposal\":\"VALID\",\"userIntent\":\"STATEMENT\",\"goalEvidence\":[],\"resourceEvidence\":[],\"corrections\":[],\"hintVi\":null},\"dialogue\":{\"proposedAct\":\"REACT_AND_ASK\",\"spokenReply\":\"What would you like?\",\"continuationType\":\"QUESTION\",\"primaryQuestion\":\"What would you like?\",\"targetGoalId\":null},\"sessionEnded\":true}";
        var gateway = gateway(request -> response(200, wrapper(candidate), metadata(200)));

        var outcome = gateway.assess(request("hello"));

        var success = assertInstanceOf(GatewayOutcome.Success.class, outcome);
        assertTrue(success.draft().candidateJson().contains("sessionEnded"));
        var validator = new SpeakingDraftValidator(mapper);
        assertThrows(SpeakingDraftValidationException.class,
                () -> validator.validateSchema(success.draft().candidateJson()));
    }

    @Test
    void gatewayHasNoRuntimeOrPersistenceDependency() {
        var forbidden = Arrays.stream(GeminiSpeakingAiGateway.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .filter(type -> type.contains("Repository") || type.contains("SessionService")
                        || type.contains("RealtimeService") || type.contains("SpeechSynthesis"))
                .toList();

        assertTrue(forbidden.isEmpty(), () -> "Unexpected runtime dependency: " + forbidden);
    }

    private GeminiSpeakingAiGateway gateway(GeminiSpeakingAiGateway.Transport transport) {
        return new GeminiSpeakingAiGateway(mapper, new PromptV2Builder(mapper),
                "https://provider.invalid/models", "gemini-test", "test-key", Duration.ofSeconds(1), transport);
    }

    private GeminiSpeakingAiGateway.ProviderHttpResponse response(int status, String body,
            ProviderRawDraft.ProviderMetadata metadata) {
        return new GeminiSpeakingAiGateway.ProviderHttpResponse(status, body, metadata);
    }

    private ProviderRawDraft.ProviderMetadata metadata(int status) {
        return new ProviderRawDraft.ProviderMetadata("gemini-test", status, "response-1", "STOP",
                null, null, null);
    }

    private String wrapper(String candidate) {
        try {
            return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", candidate)))))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private SpeakingAiRequestV1 request(String transcript) {
        return new SpeakingAiRequestV1("speaking.ai.draft.v1", "speaking.ai.prompt.v2",
                new SpeakingAiRequestV1.PinnedSnapshot("item-v1", "version-v1", "Cafe", "Order a drink",
                        List.of(new SpeakingAiRequestV1.PinnedGoal("coffee", "Coffee", "order coffee",
                                SpeakingGoal.Priority.REQUIRED)), List.of("coffee"), List.of("Could I have")),
                List.of("coffee"),
                List.of(new SpeakingAiRequestV1.HistoryTurn(SpeakingAiRequestV1.Role.LEARNER, "Hello."),
                        new SpeakingAiRequestV1.HistoryTurn(SpeakingAiRequestV1.Role.PARTNER, "Hi there.")),
                List.of(new SpeakingAiRequestV1.PinnedResource("coffee", "VOCABULARY", "coffee")), transcript);
    }
}
