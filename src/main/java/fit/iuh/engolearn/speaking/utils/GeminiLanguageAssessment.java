package fit.iuh.engolearn.speaking.utils;

import com.google.genai.errors.ApiException;
import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Message;
import fit.iuh.engolearn.speaking.utils.port.LanguageAssessmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class GeminiLanguageAssessment implements LanguageAssessmentPort {
    private static final String ASSESSMENT_COMMAND = "Evaluate only the indexed learner speech and return the rubric JSON.";
    private final SpeakingProviderProperties properties;
    private final GeminiStructuredSpeakingGeneration generation;

    @Override
    public String assess(String system, String data, List<Message> input, String schema, Duration timeout) {
        if (!properties.getAssessment().isLanguageEnabled()) throw new Failure("LANGUAGE_PROVIDER_DISABLED");
        String key = properties.getGemini().getApiKey();
        if (key == null || key.isBlank()) throw new Failure("LANGUAGE_PROVIDER_NOT_CONFIGURED");
        try {
            var messages = new ArrayList<>(input);
            // Gemini requires the request to end in a user turn, including after a delivered AI reply.
            messages.add(new Message("user", ASSESSMENT_COMMAND));
            return generation.generateStructured(system, data, messages, schema, timeout, 4096);
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            while (cause != null) {
                if (cause instanceof ApiException api && api.code() == 429) throw new Failure("LANGUAGE_PROVIDER_QUOTA");
                if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
                    throw new Failure("LANGUAGE_PROVIDER_TIMEOUT");
                }
                cause = cause.getCause();
            }
            throw new Failure("LANGUAGE_PROVIDER_UNAVAILABLE");
        }
    }
}
