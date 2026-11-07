package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GeminiSpeakingOpeningGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generatesValidatedOpeningUsingPinnedRolesAndContext() throws Exception {
        HttpRequest[] sent = new HttpRequest[1];
        var generator = generator(request -> {
            sent[0] = request;
            return response(200, wrapper("{\"reply\":\"Welcome to my cafe.\",\"hintExampleEn\":\"I'd like a coffee, please.\"}"));
        });

        var draft = generator.generate(content());

        assertEquals("Welcome to my cafe.", draft.reply());
        assertEquals("I'd like a coffee, please.", draft.hintExampleEn());
        assertEquals(Duration.ofSeconds(10), sent[0].timeout().orElseThrow());
        assertEquals("secret", sent[0].headers().firstValue("x-goog-api-key").orElseThrow());
        assertTrue(sent[0].uri().toString().contains("gemini-test:generateContent"));
    }

    @Test
    void rejectsMissingBlankOrNonTextOpeningProperties() {
        for (String draft : List.of(
                "{\"reply\":\"Hello.\"}",
                "{\"reply\":\" \",\"hintExampleEn\":\"Answer me.\"}",
                "{\"reply\":\"Hello.\",\"hintExampleEn\":3}")) {
            assertThrows(IllegalStateException.class, () -> generator(request -> response(200, wrapper(draft)))
                    .generate(content()));
        }
    }

    @Test
    void rejectsProviderFailuresAndMalformedWrapper() {
        assertThrows(IllegalStateException.class,
                () -> generator(request -> response(429, "provider details" )).generate(content()));
        assertThrows(IllegalStateException.class,
                () -> generator(request -> response(200, "{bad json")).generate(content()));
    }

    @Test
    void failsWithoutCallingProviderWhenApiKeyIsMissing() {
        var calls = new int[1];
        var generator = new GeminiSpeakingOpeningGenerator(mapper, "", "model", "https://example.test", request -> {
            calls[0]++;
            return response(200, "{}");
        });
        assertThrows(IllegalStateException.class, () -> generator.generate(content()));
        assertEquals(0, calls[0]);
    }

    private GeminiSpeakingOpeningGenerator generator(GeminiSpeakingOpeningGenerator.Transport transport) {
        return new GeminiSpeakingOpeningGenerator(mapper, "secret", "gemini-test",
                "https://example.test/v1beta/models", transport);
    }

    private SpeakingContentSnapshot content() {
        return new SpeakingContentSnapshot("item", "version", "Cafe", "Description",
                List.of(new SpeakingGoal("g1", "Order", "Order a drink", SpeakingGoal.Priority.REQUIRED)),
                null, 2, List.of(), List.of(), 1L, "REQUIRED_GOALS_V2",
                "A small cafe", "customer", "barista", null);
    }

    private String wrapper(String candidateText) {
        try {
            return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                    Map.of("parts", List.of(Map.of("text", candidateText)))))));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return HttpRequest.newBuilder().uri(java.net.URI.create("https://example.test")).build(); }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
            @Override public java.net.URI uri() { return java.net.URI.create("https://example.test"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
