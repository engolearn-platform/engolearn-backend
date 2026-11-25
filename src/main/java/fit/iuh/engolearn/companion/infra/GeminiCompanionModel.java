package fit.iuh.engolearn.companion.infra;

import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionModel;
import fit.iuh.engolearn.config.CompanionProperties;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class GeminiCompanionModel implements CompanionModel {
    private static final Logger log = LoggerFactory.getLogger(GeminiCompanionModel.class);

    private final ChatModel model;

    public GeminiCompanionModel(CompanionProperties properties) {
        this.model = createModel(properties);
    }

    @Override
    public Flux<String> stream(Prompt prompt) {
        if (model == null) {
            return Flux.error(providerUnavailable());
        }
        return model.stream(prompt)
                .doOnNext(this::logUsageIfPresent)
                .flatMapIterable(ChatResponse::getResults)
                .mapNotNull(result -> result == null || result.getOutput() == null ? null : result.getOutput().getText())
                .filter(text -> text != null && !text.isEmpty())
                .onErrorMap(this::providerFailure);
    }

    private ChatModel createModel(CompanionProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return null;
        }

        GoogleGenAiChatModel.ChatModel configuredModel = Arrays.stream(GoogleGenAiChatModel.ChatModel.values())
                .filter(candidate -> candidate.getValue().equals(properties.getModel()))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        var httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(properties.getInferenceTimeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build())
                .build();
        var client = com.google.genai.Client.builder()
                .apiKey(properties.getApiKey())
                .httpOptions(httpOptions)
                .build();
        var options = GoogleGenAiChatOptions.builder()
                .model(configuredModel)
                .temperature(properties.getTemperature())
                .maxOutputTokens(properties.getMaxOutputTokens())
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(options)
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }

    private void logUsageIfPresent(ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            log.debug("Gemini companion usage: promptTokens={}, completionTokens={}, totalTokens={}",
                    promptTokens, completionTokens, totalTokens);
        }
    }

    private CompanionException providerUnavailable() {
        return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE",
                "The companion service is temporarily unavailable", false);
    }

    private CompanionException providerFailure(Throwable failure) {
        if (failure instanceof CompanionException companionFailure) {
            return companionFailure;
        }
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
            if (cause instanceof ApiException apiFailure) {
                return apiFailure.code() == 408 ? 504 : apiFailure.code();
            }
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return 504;
            }
        }
        return null;
    }

    private boolean isInferenceTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiFailure && apiFailure.code() == 408) {
                return true;
            }
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTransientProviderFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException apiFailure) {
                int status = apiFailure.code();
                return status >= 500;
            }
            if (cause instanceof GenAiIOException || cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
