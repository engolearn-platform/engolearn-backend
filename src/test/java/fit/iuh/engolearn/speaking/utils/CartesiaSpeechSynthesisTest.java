package fit.iuh.engolearn.speaking.utils;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CartesiaSpeechSynthesisTest {
    @Test void sendsVoiceIdAndMp3FormatToBytesApi() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        byte[] audio = {1, 2, 3};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tts/bytes", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                payload.set(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(200, audio.length);
                exchange.getResponseBody().write(audio);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            CartesiaSpeechSynthesis synthesis = new CartesiaSpeechSynthesis(mapper, "test-key", "voice-id",
                    "sonic-3.6", "http://127.0.0.1:" + server.getAddress().getPort() + "/tts/bytes",
                    "2026-08-14");

            assertArrayEquals(audio, synthesis.synthesize("Hello!"));
            assertEquals("Bearer test-key", authorization.get());
            assertEquals("voice-id", payload.get().path("voice").asText());
            assertEquals("mp3", payload.get().path("output_format").path("container").asText());
            assertEquals("Hello!", payload.get().path("transcript").asText());
        } finally {
            server.stop(0);
        }
    }
}
