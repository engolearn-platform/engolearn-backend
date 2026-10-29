package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechSynthesisPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class CartesiaSpeechSynthesis implements SpeechSynthesisPort {
    private final ObjectMapper mapper;
    private volatile HttpClient client;
    private final String apiKey;
    private final String voice;
    private final String model;
    private final String endpoint;
    private final String apiVersion;

    public CartesiaSpeechSynthesis(ObjectMapper mapper,
            @Value("${engolearn.speaking.cartesia.api-key:}") String apiKey,
            @Value("${engolearn.speaking.cartesia.voice:db6b0ed5-d5d3-463d-ae85-518a07d3c2b4}") String voice,
            @Value("${engolearn.speaking.cartesia.model:sonic-3.6}") String model,
            @Value("${engolearn.speaking.cartesia.endpoint:https://api.cartesia.ai/tts/bytes}") String endpoint,
            @Value("${engolearn.speaking.cartesia.api-version:2026-08-14}") String apiVersion) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.voice = voice;
        this.model = model;
        this.endpoint = endpoint;
        this.apiVersion = apiVersion;
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) return new byte[0];
        if (apiKey.isBlank()) throw new IllegalStateException("Cartesia API key is not configured");
        try {
            String body = mapper.writeValueAsString(Map.of("model_id", model, "transcript", text,
                    "voice", voice, "language", "en", "output_format",
                    Map.of("container", "mp3", "sample_rate", 24000, "bit_rate", 128000)));
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20)).header("Cartesia-Version", apiVersion)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Cartesia returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cartesia request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Cartesia synthesis failed", ex);
        }
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = HttpClient.newHttpClient();
                current = client;
            }
        }
        return current;
    }
}
