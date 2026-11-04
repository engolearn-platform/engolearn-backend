package fit.iuh.engolearn.speaking.utils;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartesiaSpeechSynthesisTest {
    @Test void sendsVoiceIdAndMp3FormatToBytesApi() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] audio = {1, 2, 3};
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(audio);
        AtomicReference<HttpRequest> request = new AtomicReference<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    request.set(invocation.getArgument(0));
                    return response;
                });

        CartesiaSpeechSynthesis synthesis = new CartesiaSpeechSynthesis(mapper, "test-key", "voice-id",
                "sonic-3.6", "http://cartesia.test/tts/bytes", "2026-08-14", client);

        assertArrayEquals(audio, synthesis.synthesize("Hello!"));
        assertEquals("http://cartesia.test/tts/bytes", request.get().uri().toString());
        assertEquals("Bearer test-key", request.get().headers().firstValue("Authorization").orElseThrow());
        JsonNode payload = mapper.readTree(readBody(request.get().bodyPublisher().orElseThrow()));
        assertEquals("voice-id", payload.path("voice").asText());
        assertEquals("mp3", payload.path("output_format").path("container").asText());
        assertEquals("Hello!", payload.path("transcript").asText());
    }

    private String readBody(HttpRequest.BodyPublisher publisher) {
        CompletableFuture<String> body = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final StringBuilder content = new StringBuilder();

            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                content.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }

            @Override public void onComplete() { body.complete(content.toString()); }
        });
        return body.join();
    }
}
