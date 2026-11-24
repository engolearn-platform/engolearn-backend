package fit.iuh.engolearn.speaking.utils;

import com.sun.net.httpserver.HttpServer;
import fit.iuh.engolearn.speaking.model.vo.SpeakingSnapshotVo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class GeminiSpeakingGenerationTest {
    @Test
    void sendsNativeSchemaAndReturnsRawTextWithoutHiddenRetries() throws IOException {
        var mapper = new ObjectMapper();
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var paths = new CopyOnWriteArrayList<String>();
        var endpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            requests.add(mapper.readTree(exchange.getRequestBody()));
            boolean success = requests.size() == 1;
            String json = success ? mapper.writeValueAsString(Map.of("modelVersion", "fixture-model", "candidates", List.of(Map.of(
                    "content", Map.of("role", "model", "parts", List.of(Map.of("text", "{\"reply\":\"Hello\"}"))),
                    "finishReason", "STOP")))) : """
                    {"error":{"code":503,"message":"Temporary failure in fixture","status":"UNAVAILABLE"}}
                    """;
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(success ? 200 : 503, payload.length);
            try (var body = exchange.getResponseBody()) {
                body.write(payload);
            }
        });
        endpoint.start();
        var properties = new SpeakingProviderProperties();
        properties.getGemini().setApiKey("local-fixture-key");
        properties.getGemini().setEndpoint("http://127.0.0.1:" + endpoint.getAddress().getPort() + "/v1beta/models");
        properties.getGemini().setModel("fixture-model");
        properties.getGemini().setOpeningTimeout(Duration.ofSeconds(3));
        var generation = new GeminiSpeakingGeneration(properties);
        try {
            var opening = new SpeakingConversation(mapper).opening(SpeakingSnapshotVo.builder().title("Cafe").build());
            assertThat(generation.generate(opening)).isEqualTo("{\"reply\":\"Hello\"}");
            assertThatThrownBy(() -> generation.generateStructured("Rubric", "original transcript", List.of(),
                    "{\"type\":\"object\",\"properties\":{\"accuracy\":{\"type\":\"integer\"}}}",
                    Duration.ofSeconds(3), 100)).isInstanceOf(RuntimeException.class);
            assertThat(paths).containsExactly("/v1beta/models/fixture-model:generateContent",
                    "/v1beta/models/fixture-model:generateContent");
            JsonNode config = requests.get(0).path("generationConfig");
            assertThat(config.path("responseMimeType").asString()).isEqualTo("application/json");
            var schema = config.path("responseJsonSchema").path("properties");
            assertThat(schema.has("memoryDelta")).isTrue();
            assertThat(schema.path("hint").path("anyOf").path(1).path("type").asString()).isEqualTo("null");
            assertThat(requests.get(1).path("generationConfig").path("responseJsonSchema")
                    .path("properties").has("accuracy")).isTrue();
            assertThat(requests.get(0).path("tools").isMissingNode()).isTrue();
        } finally {
            generation.close();
            endpoint.stop(0);
        }
    }
}
