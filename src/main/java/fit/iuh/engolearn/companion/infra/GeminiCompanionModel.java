package fit.iuh.engolearn.companion.infra;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionModel;
import fit.iuh.engolearn.companion.service.CompanionModelContinuation;
import fit.iuh.engolearn.companion.service.CompanionModelEvent;
import fit.iuh.engolearn.companion.service.CompanionModelRequest;
import fit.iuh.engolearn.companion.service.CompanionPromptMessage;
import fit.iuh.engolearn.companion.service.CompanionToolCall;
import fit.iuh.engolearn.companion.service.CompanionToolDefinition;
import fit.iuh.engolearn.companion.service.CompanionToolResult;
import fit.iuh.engolearn.companion.service.CompanionUsage;
import fit.iuh.engolearn.config.CompanionProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/** Gemini provider adapter. Google SDK types and native continuation data stay inside infra. */
@Component
public class GeminiCompanionModel implements CompanionModel {
    private static final Logger log = LoggerFactory.getLogger(GeminiCompanionModel.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CompanionProperties properties;
    private final GeminiContentTransport transport;

    @Autowired
    public GeminiCompanionModel(CompanionProperties properties) {
        this(properties, createTransport(properties));
    }

    GeminiCompanionModel(CompanionProperties properties, GeminiContentTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public Flux<CompanionModelEvent> stream(CompanionModelRequest request) {
        if (transport == null) {
            return Flux.error(providerUnavailable());
        }
        return Flux.create(sink -> {
            AtomicReference<GeminiContentTransport.GeminiContentStream> activeStream = new AtomicReference<>();
            AtomicReference<Disposable> worker = new AtomicReference<>();
            sink.onCancel(() -> {
                Disposable scheduled = worker.get();
                if (scheduled != null) scheduled.dispose();
                GeminiContentTransport.GeminiContentStream stream = activeStream.get();
                if (stream != null) stream.close();
            });
            Disposable scheduled = Schedulers.boundedElastic().schedule(() -> {
                try (GeminiContentTransport.GeminiContentStream stream = transport.stream(
                        properties.getModel(), contents(request), config(request))) {
                    activeStream.set(stream);
                    if (sink.isCancelled()) return;
                    emitRound(request, stream, sink::next);
                    if (!sink.isCancelled()) sink.complete();
                } catch (Throwable failure) {
                    if (!sink.isCancelled()) sink.error(providerFailure(failure));
                } finally {
                    activeStream.set(null);
                }
            });
            worker.set(scheduled);
            if (sink.isCancelled()) scheduled.dispose();
        });
    }

    private void emitRound(CompanionModelRequest request, Iterable<GenerateContentResponse> responses,
                           java.util.function.Consumer<CompanionModelEvent> emit) {
        Long totalTokenCount = null;
        boolean toolCallSeen = false;
        List<Part> nativeResponseParts = new ArrayList<>();
        Map<String, CompanionToolCall> callsById = new LinkedHashMap<>();
        boolean blockedFinish = false;

        for (GenerateContentResponse response : responses) {
            totalTokenCount = authoritativeTotal(response).orElse(totalTokenCount);
            Candidate candidate = firstCandidate(response);
            if (candidate == null) continue;
            blockedFinish |= isBlockedFinish(candidate.finishReason().orElse(null));
            if (candidate.content().isEmpty()) continue;
            for (Part part : candidate.content().orElseThrow().parts().orElse(List.of())) {
                nativeResponseParts.add(part);
                FunctionCall functionCall = part.functionCall().orElse(null);
                if (functionCall != null) {
                    toolCallSeen = true;
                    String callId = functionCall.id().orElse("");
                    String name = functionCall.name().orElse("");
                    Map<String, Object> arguments = functionCall.args().orElse(Map.of());
                    try {
                        callsById.put(callId, new CompanionToolCall(callId, name, arguments));
                    } catch (IllegalArgumentException malformedCall) {
                        throw invalidToolCall();
                    }
                    continue;
                }
                String text = part.text().orElse("");
                if (!text.isEmpty() && !part.thought().orElse(false) && !toolCallSeen) {
                    emit.accept(new CompanionModelEvent.TextDelta(text));
                }
            }
        }

        if (totalTokenCount == null || totalTokenCount < 0) {
            throw usageUnavailable();
        }
        if (blockedFinish) throw providerUnavailable();
        if (toolCallSeen) {
            Content continuation = Content.builder().role("model").parts(nativeResponseParts).build();
            emit.accept(new CompanionModelEvent.ToolCalls(
                    encodeContinuation(request, continuation), List.copyOf(callsById.values())));
        }
        emit.accept(new CompanionModelEvent.RoundCompleted(new CompanionUsage(totalTokenCount)));
    }

    private List<Content> contents(CompanionModelRequest request) {
        GeminiContinuation nativeContinuation = decodeContinuation(request.continuation());
        if (nativeContinuation != null) {
            List<Content> contents = new ArrayList<>(nativeContinuation.transcript());
            List<Part> resultParts = request.toolResults().stream().map(this::functionResponsePart).toList();
            if (!resultParts.isEmpty()) contents.add(Content.builder().role("user").parts(resultParts).build());
            return List.copyOf(contents);
        }
        List<Content> contents = new ArrayList<>();
        for (var message : request.messages()) {
            if (message.role() != CompanionPromptMessage.Role.SYSTEM) {
                contents.add(Content.builder()
                        .role(message.role() == CompanionPromptMessage.Role.USER
                                ? "user" : "model")
                        .parts(Part.fromText(message.text()))
                        .build());
            }
        }
        if (contents.stream().noneMatch(content -> content.role().orElse("").equals("user"))) {
            throw new IllegalArgumentException("A user message is required for inference");
        }
        return contents;
    }

    private GenerateContentConfig config(CompanionModelRequest request) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .temperature((float) properties.getTemperature())
                .maxOutputTokens(properties.getMaxOutputTokens())
                .candidateCount(1)
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build());
        Content systemInstruction = systemInstruction(request);
        if (systemInstruction != null) {
            builder.systemInstruction(systemInstruction);
        }
        if (!request.tools().isEmpty()) {
            List<FunctionDeclaration> declarations = request.tools().stream().map(this::functionDeclaration).toList();
            builder.tools(Tool.builder().functionDeclarations(declarations).build());
            builder.toolConfig(ToolConfig.builder().functionCallingConfig(
                    FunctionCallingConfig.builder().mode("AUTO").build()).build());
        }
        return builder.build();
    }

    private FunctionDeclaration functionDeclaration(CompanionToolDefinition definition) {
        return FunctionDeclaration.builder()
                .name(definition.name())
                .description(definition.description())
                .parameters(Schema.fromJson(definition.inputSchemaJson()))
                .build();
    }

    private Part functionResponsePart(CompanionToolResult result) {
        Map<String, Object> response = parseResponseJson(result.json());
        return Part.builder().functionResponse(FunctionResponse.builder()
                .id(result.callId()).name(result.name()).response(response).build()).build();
    }

    private Map<String, Object> parseResponseJson(String json) {
        try {
            return JSON.readValue(json, new TypeReference<>() {});
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Tool result must be a JSON object", malformed);
        }
    }

    private CompanionModelContinuation encodeContinuation(CompanionModelRequest request, Content nativeAssistant) {
        List<Content> transcript = new ArrayList<>(contents(request));
        transcript.add(nativeAssistant);
        return new GeminiContinuation(transcript, systemInstruction(request));
    }

    private Content systemInstruction(CompanionModelRequest request) {
        GeminiContinuation nativeContinuation = decodeContinuation(request.continuation());
        if (nativeContinuation != null) {
            return nativeContinuation.systemInstruction();
        }
        List<Part> systemParts = request.messages().stream()
                .filter(message -> message.role() == CompanionPromptMessage.Role.SYSTEM)
                .map(message -> Part.fromText(message.text())).toList();
        return systemParts.isEmpty() ? null : Content.fromParts(systemParts.toArray(Part[]::new));
    }

    private GeminiContinuation decodeContinuation(CompanionModelContinuation continuation) {
        if (continuation == null) return null;
        if (continuation instanceof GeminiContinuation nativeContinuation) return nativeContinuation;
        throw providerUnavailable();
    }

    private boolean isBlockedFinish(FinishReason reason) {
        if (reason == null) return false;
        String value = reason.toString();
        return value.equals("SAFETY") || value.equals("BLOCKLIST") || value.equals("PROHIBITED_CONTENT")
                || value.equals("SPII") || value.equals("RECITATION") || value.equals("LANGUAGE");
    }

    private java.util.Optional<Long> authoritativeTotal(GenerateContentResponse response) {
        return response.usageMetadata()
                .flatMap(GenerateContentResponseUsageMetadata::totalTokenCount)
                .map(Integer::longValue);
    }

    private Candidate firstCandidate(GenerateContentResponse response) {
        List<Candidate> candidates = response.candidates().orElse(List.of());
        return candidates.stream().filter(candidate -> candidate.index().orElse(0) == 0).findFirst().orElse(null);
    }

    private static GeminiContentTransport createTransport(CompanionProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return null;
        Duration timeout = properties.getInferenceTimeout();
        var httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(timeout.toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build())
                .build();
        Client client = Client.builder().apiKey(properties.getApiKey()).httpOptions(httpOptions).build();
        return new GoogleGenAiContentTransport(client);
    }

    @PreDestroy
    void closeTransport() {
        if (transport != null) transport.close();
    }

    private CompanionException providerUnavailable() {
        return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE",
                "The companion service is temporarily unavailable", false);
    }

    private CompanionException usageUnavailable() {
        return new CompanionException(503, "COMPANION_USAGE_UNAVAILABLE",
                "The companion service is temporarily unavailable", false);
    }

    private CompanionException invalidToolCall() {
        return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE",
                "The companion service is temporarily unavailable", false);
    }

    private CompanionException providerFailure(Throwable failure) {
        if (failure instanceof CompanionException companionFailure) return companionFailure;
        if (failure instanceof CancellationException) {
            return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE",
                    "The companion request was cancelled", false);
        }
        if (isInferenceTimeout(failure)) {
            log.debug("Gemini companion call failed: status=504, causeClass={}, retryable=true",
                    failure.getClass().getSimpleName());
            return new CompanionException(504, "COMPANION_DEADLINE_EXCEEDED",
                    "The response took too long. Retry this turn.", true);
        }
        boolean retryable = hasTransientProviderFailure(failure);
        log.debug("Gemini companion call failed: status={}, causeClass={}, retryable={}",
                providerStatusCode(failure), failure.getClass().getSimpleName(), retryable);
        return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE",
                "The companion service is temporarily unavailable", retryable);
    }

    private Integer providerStatusCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiFailure) return apiFailure.code() == 408 ? 504 : apiFailure.code();
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) return 504;
        }
        return null;
    }

    private boolean isInferenceTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiFailure && apiFailure.code() == 408) return true;
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private boolean hasTransientProviderFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiFailure) return apiFailure.code() >= 500;
            if (cause instanceof GenAiIOException || cause instanceof IOException) return true;
        }
        return false;
    }

    private record GeminiContinuation(List<Content> transcript, Content systemInstruction)
            implements CompanionModelContinuation {
        private GeminiContinuation {
            transcript = List.copyOf(transcript);
        }

        @Override
        public String toString() {
            return "GeminiContinuation[messageCount=" + transcript.size() + ", systemInstruction=redacted]";
        }
    }
}
