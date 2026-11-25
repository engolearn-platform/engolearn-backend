package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechSynthesisPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.Map;

@Component
public class CartesiaSpeechSynthesis implements SpeechSynthesisPort {
    private static final int SAMPLE_RATE = 24_000;
    private final ObjectMapper mapper;
    private volatile HttpClient client;
    private final String apiKey;
    private final String voice;
    private final String model;
    private final String endpoint;
    private final String apiVersion;
    private final Duration timeout;

    @Autowired
    public CartesiaSpeechSynthesis(ObjectMapper mapper, SpeakingProviderProperties properties) {
        this(mapper, properties.getCartesia().getApiKey(), properties.getCartesia().getVoice(),
                properties.getCartesia().getModel(), properties.getCartesia().getEndpoint(),
                properties.getCartesia().getApiVersion(), properties.getCartesia().getTimeout(), null);
    }

    CartesiaSpeechSynthesis(ObjectMapper mapper, String apiKey, String voice, String model,
            String endpoint, String apiVersion, Duration timeout, HttpClient client) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.voice = voice;
        this.model = model;
        this.endpoint = endpoint;
        this.apiVersion = apiVersion;
        this.timeout = timeout;
        this.client = client;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Cartesia timeout must be positive");
        }
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) return new byte[0];
        if (apiKey.isBlank()) throw new IllegalStateException("Cartesia API key is not configured");
        if (voice == null || voice.isBlank()) throw new IllegalStateException("Cartesia voice ID is not configured");
        try {
            String body = mapper.writeValueAsString(Map.of("model_id", model, "transcript", text,
                    "voice", voice, "language", "en", "output_format",
                    Map.of("container", "raw", "sample_rate", SAMPLE_RATE, "encoding", "pcm_s16le")));
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .timeout(timeout).header("Cartesia-Version", apiVersion)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<byte[]> response = client().send(request, ignored -> new LimitedAudioBody());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Cartesia returned HTTP " + response.statusCode());
            }
            var type = response.headers().firstValue("Content-Type").map(MediaType::parseMediaType)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            byte[] pcm = response.body();
            if ((!"audio".equalsIgnoreCase(type.getType()) && !MediaType.APPLICATION_OCTET_STREAM.includes(type))
                    || pcm.length == 0 || pcm.length % Short.BYTES != 0) {
                throw new IllegalStateException("Cartesia response is not complete PCM16 audio");
            }
            return SpeakingWav.pcm16Mono(pcm, SAMPLE_RATE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cartesia request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Cartesia synthesis failed", ex);
        }
    }

    private static final class LimitedAudioBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;

        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) size += chunk.remaining();
            if (size > SpeakingWav.MAX_REPLY_BYTES - SpeakingWav.PCM_HEADER_BYTES) {
                subscription.cancel();
                onError(new IllegalStateException("Cartesia audio exceeds the response limit"));
                return;
            }
            delegate.onNext(chunks);
        }

        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
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
