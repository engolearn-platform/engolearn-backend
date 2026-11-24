package fit.iuh.engolearn.speaking.utils;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Prompt;
import fit.iuh.engolearn.speaking.utils.port.ConversationGenerationPort;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Component
public class GeminiSpeakingGeneration implements ConversationGenerationPort {
    private final SpeakingProviderProperties.Gemini properties;
    private final Map<Duration, Gateway> gateways = new HashMap<>();

    private record Gateway(Client client, GoogleGenAiChatModel model) {}

    public GeminiSpeakingGeneration(SpeakingProviderProperties properties) {
        this.properties = properties.getGemini();
    }

    @Override
    public String generate(Prompt prompt) {
        return generateStructured(prompt.system(), prompt.data(), prompt.messages(), SpeakingConversation.SCHEMA,
                prompt.opening() ? properties.getOpeningTimeout() : properties.getConversationTimeout(), 1200);
    }

    public String generateStructured(String system, String data, List<SpeakingConversation.Message> input,
                                     String schema, Duration timeout, int maxOutputTokens) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        // Encode trusted prompt instructions separately from serialized learner-owned data.
        if (data != null) messages.add(new UserMessage(data));
        for (var message : input) {
            messages.add("assistant".equals(message.role()) ? new AssistantMessage(message.text())
                    : new UserMessage(message.text()));
        }
        var options = GoogleGenAiChatOptions.builder().model(properties.getModel())
                .maxOutputTokens(maxOutputTokens).responseMimeType("application/json").outputSchema(schema).build();
        var response = gateway(timeout).model().call(new org.springframework.ai.chat.prompt.Prompt(messages, options));
        return response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
    }

    private synchronized Gateway gateway(Duration timeout) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Gemini timeout must be positive");
        }
        return gateways.computeIfAbsent(timeout, this::create);
    }

    private Gateway create(Duration timeout) {
        URI endpoint = URI.create(properties.getEndpoint());
        String[] path = endpoint.getPath().split("/");
        if (path.length != 3 || !"models".equals(path[2])) {
            throw new IllegalStateException("Expected a Gemini endpoint ending in /<apiVersion>/models");
        }
        var http = HttpOptions.builder().baseUrl(endpoint.getScheme() + "://" + endpoint.getRawAuthority())
                .apiVersion(path[1]).timeout(Math.toIntExact(timeout.toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
        var client = Client.builder().apiKey(properties.getApiKey()).httpOptions(http).build();
        var options = GoogleGenAiChatOptions.builder().model(properties.getModel()).build();
        var retry = new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
        return new Gateway(client, GoogleGenAiChatModel.builder().genAiClient(client).options(options)
                .retryTemplate(retry).build());
    }

    @PreDestroy
    synchronized void close() {
        gateways.values().forEach(gateway -> gateway.client().close());
        gateways.clear();
    }
}
