package fit.iuh.engolearn.companion.infra;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import fit.iuh.engolearn.companion.service.MemoryEmbeddingService;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import fit.iuh.engolearn.config.CompanionProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GeminiMemoryEmbeddingService implements MemoryEmbeddingService {
    private final CompanionMemoryProperties memoryProperties;
    private final Client client;

    public GeminiMemoryEmbeddingService(CompanionMemoryProperties memoryProperties, CompanionProperties properties) {
        this.memoryProperties = memoryProperties;
        this.client = properties.getApiKey() == null || properties.getApiKey().isBlank()
                ? null : Client.builder().apiKey(properties.getApiKey()).build();
    }

    @Override
    public List<Float> embedQuery(String query, Duration timeout) {
        return embed("task: search query | query: " + query, timeout);
    }

    @Override
    public List<Float> embedDocument(String content, Duration timeout) {
        return embed("task: search result | title: none | text: " + content, timeout);
    }

    private List<Float> embed(String input, Duration timeout) {
        if (!memoryProperties.isEnabled() || client == null) throw new IllegalStateException("Memory embedding unavailable");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Embedding timeout must be positive");
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(memoryProperties.getDimensions())
                .httpOptions(HttpOptions.builder().timeout(timeoutMillis)
                        .retryOptions(HttpRetryOptions.builder().attempts(2).build()).build())
                .build();
        EmbedContentResponse response = client.models.embedContent(memoryProperties.getEmbeddingModel(), input, config);
        List<Float> values = response.embeddings().orElseThrow(() -> new IllegalStateException("Embedding response is empty"))
                .getFirst().values().orElseThrow(() -> new IllegalStateException("Embedding values are empty"));
        if (values.size() != memoryProperties.getDimensions() || values.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalStateException("Embedding response has invalid dimensions");
        }
        return List.copyOf(values);
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
