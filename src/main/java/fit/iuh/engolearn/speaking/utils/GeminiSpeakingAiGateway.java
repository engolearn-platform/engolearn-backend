package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.GatewayOutcome;
import fit.iuh.engolearn.speaking.model.ProviderRawDraft;
import fit.iuh.engolearn.speaking.model.SpeakingAiRequestV1;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GeminiSpeakingAiGateway implements SpeakingAiGateway {
    private final ObjectMapper mapper;
    private final PromptV2Builder promptBuilder;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final Transport transport;

    public GeminiSpeakingAiGateway(ObjectMapper mapper, PromptV2Builder promptBuilder,
            String endpoint, String model, String apiKey, Duration timeout) {
        this(mapper, promptBuilder, endpoint, model, apiKey, timeout,
                new JdkTransport(HttpClient.newBuilder().connectTimeout(timeout).build()));
    }

    GeminiSpeakingAiGateway(ObjectMapper mapper, PromptV2Builder promptBuilder,
            String endpoint, String model, String apiKey, Duration timeout, Transport transport) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.endpoint = requireNonBlank(endpoint, "endpoint");
        this.model = requireNonBlank(model, "model");
        this.apiKey = requireNonBlank(apiKey, "apiKey");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public GatewayOutcome assess(SpeakingAiRequestV1 request) {
        Objects.requireNonNull(request, "request");
        ProviderHttpRequest httpRequest = requestFor(promptBuilder.build(request));
        final ProviderHttpResponse response;
        try {
            response = transport.send(httpRequest);
        } catch (HttpTimeoutException exception) {
            return failure(new GatewayOutcome.Timeout(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.TIMEOUT, message(exception, "Gemini request timed out"), null, null, exception)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(new GatewayOutcome.Interrupted(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.INTERRUPTED, message(exception, "Gemini request interrupted"), null, null, exception)));
        } catch (IOException exception) {
            return failure(new GatewayOutcome.TransportFailure(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.TRANSPORT, message(exception, "Gemini transport failed"), null, null, exception)));
        }

        if (response == null || response.statusCode() == null) {
            return failure(new GatewayOutcome.MissingProviderMetadata(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MISSING_METADATA, "Provider HTTP status is missing", null,
                    response == null ? null : response.metadata(), null)));
        }

        if (response.statusCode() / 100 != 2) return httpFailure(response);
        if (response.metadata() == null || !response.metadata().hasRequiredFields()) {
            return failure(new GatewayOutcome.MissingProviderMetadata(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MISSING_METADATA, "Required provider metadata is missing",
                    response.statusCode(), response.metadata(), null)));
        }

        final JsonNode wrapper;
        try {
            wrapper = mapper.readTree(response.body());
        } catch (Exception exception) {
            return failure(new GatewayOutcome.MalformedProviderResponse(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MALFORMED_WRAPPER, "Provider wrapper is not valid JSON",
                    response.statusCode(), response.metadata(), exception)));
        }
        if (wrapper == null || !wrapper.isObject()) {
            return failure(new GatewayOutcome.MalformedProviderResponse(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MALFORMED_WRAPPER, "Provider wrapper must be a JSON object",
                    response.statusCode(), response.metadata(), null)));
        }

        final JsonNode candidate;
        try {
            candidate = firstCandidate(wrapper);
        } catch (MalformedWrapperException exception) {
            return malformedWrapper(response, exception.getMessage());
        }
        if (candidate == null) return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        if (!candidate.isObject()) {
            return failure(new GatewayOutcome.MalformedProviderResponse(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MALFORMED_WRAPPER, "Provider candidate must be an object",
                    response.statusCode(), response.metadata(), null)));
        }

        JsonNode content = candidate.get("content");
        if (content == null || content.isNull()) {
            return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        }
        if (!content.isObject()) return malformedWrapper(response, "Provider candidate content must be an object");
        JsonNode parts = content.get("parts");
        if (parts == null || parts.isNull()) {
            return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        }
        if (!parts.isArray()) return malformedWrapper(response, "Provider candidate parts must be an array");
        if (parts.isEmpty()) return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        JsonNode firstPart = parts.get(0);
        if (firstPart == null || firstPart.isNull()) {
            return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        }
        if (!firstPart.isObject()) return malformedWrapper(response, "Provider candidate part must be an object");
        JsonNode text = firstPart.get("text");
        if (text == null || text.isNull()) {
            return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        }
        if (!text.isTextual()) return malformedWrapper(response, "Provider candidate text must be a string");
        String candidateJson = text.asText();
        if (candidateJson.isBlank()) {
            return new GatewayOutcome.EmptyCandidate(enrichMetadata(response.metadata(), wrapper, response.statusCode()));
        }
        try {
            mapper.readTree(candidateJson);
        } catch (Exception exception) {
            return failure(new GatewayOutcome.MalformedProviderResponse(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.MALFORMED_CANDIDATE, "Provider candidate is not valid JSON",
                    response.statusCode(), response.metadata(), exception)));
        }

        ProviderRawDraft.ProviderMetadata metadata = enrichMetadata(response.metadata(), wrapper, response.statusCode());
        return new GatewayOutcome.Success(new ProviderRawDraft(candidateJson, metadata));
    }

    private ProviderHttpRequest requestFor(String prompt) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json")));
            return new ProviderHttpRequest(URI.create(endpoint + "/" + model + ":generateContent"),
                    Map.of("Content-Type", "application/json", "x-goog-api-key", apiKey), body, timeout, model);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize Gemini request", exception);
        }
    }

    private JsonNode firstCandidate(JsonNode wrapper) {
        JsonNode candidates = wrapper.get("candidates");
        if (candidates == null || candidates.isNull()) return null;
        if (!candidates.isArray()) throw new MalformedWrapperException("Provider candidates must be an array");
        if (candidates.isEmpty()) return null;
        JsonNode candidate = candidates.get(0);
        if (candidate == null || candidate.isNull()) return null;
        return candidate;
    }

    private GatewayOutcome httpFailure(ProviderHttpResponse response) {
        GatewayOutcome.FailureCode code = response.statusCode() == 429
                ? GatewayOutcome.FailureCode.RATE_LIMITED_429
                : response.statusCode() >= 500 && response.statusCode() <= 599
                ? GatewayOutcome.FailureCode.SERVER_5XX
                : GatewayOutcome.FailureCode.OTHER_HTTP;
        return new GatewayOutcome.HttpFailure(new GatewayOutcome.ProviderFailure(code,
                "Gemini returned HTTP " + response.statusCode(), response.statusCode(), response.metadata(), null));
    }

    private GatewayOutcome malformedWrapper(ProviderHttpResponse response, String message) {
        return failure(new GatewayOutcome.MalformedProviderResponse(new GatewayOutcome.ProviderFailure(
                GatewayOutcome.FailureCode.MALFORMED_WRAPPER, message, response.statusCode(), response.metadata(), null)));
    }

    private GatewayOutcome failure(GatewayOutcome outcome) {
        return outcome;
    }

    private ProviderRawDraft.ProviderMetadata enrichMetadata(ProviderRawDraft.ProviderMetadata metadata,
            JsonNode wrapper, int statusCode) {
        String responseId = firstNonBlank(metadata.responseId(), text(wrapper, "responseId"));
        JsonNode candidate = wrapper.path("candidates").path(0);
        String finishReason = firstNonBlank(metadata.finishReason(), text(candidate, "finishReason"));
        JsonNode usage = wrapper.path("usageMetadata");
        Long promptTokens = firstNonNull(metadata.promptTokenCount(), longValue(usage, "promptTokenCount"));
        Long candidateTokens = firstNonNull(metadata.candidateTokenCount(), longValue(usage, "candidatesTokenCount"));
        Long totalTokens = firstNonNull(metadata.totalTokenCount(), longValue(usage, "totalTokenCount"));
        return new ProviderRawDraft.ProviderMetadata(metadata.model(), statusCode, responseId, finishReason,
                promptTokens, candidateTokens, totalTokens);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
        return value;
    }

    private String message(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }

    @FunctionalInterface
    interface Transport {
        ProviderHttpResponse send(ProviderHttpRequest request) throws IOException, InterruptedException;
    }

    record ProviderHttpRequest(URI uri, Map<String, String> headers, String body, Duration timeout, String model) {}

    record ProviderHttpResponse(Integer statusCode, String body, ProviderRawDraft.ProviderMetadata metadata) {}

    private static final class JdkTransport implements Transport {
        private final HttpClient client;

        private JdkTransport(HttpClient client) {
            this.client = client;
        }

        @Override
        public ProviderHttpResponse send(ProviderHttpRequest request) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(request.uri())
                    .timeout(request.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(request.body()));
            request.headers().forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ProviderRawDraft.ProviderMetadata metadata = new ProviderRawDraft.ProviderMetadata(
                    request.model(), response.statusCode(), response.headers().firstValue("x-request-id").orElse(null),
                    null, null, null, null);
            return new ProviderHttpResponse(response.statusCode(), response.body(), metadata);
        }
    }

    private static final class MalformedWrapperException extends RuntimeException {
        private MalformedWrapperException(String message) {
            super(message);
        }
    }
}
