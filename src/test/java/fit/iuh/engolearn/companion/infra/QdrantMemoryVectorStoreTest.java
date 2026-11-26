package fit.iuh.engolearn.companion.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QdrantMemoryVectorStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private AtomicReference<String> queryBody;
    private AtomicInteger queryCalls;
    private QdrantMemoryVectorStore store;

    @BeforeEach
    void startServer() throws Exception {
        queryBody = new AtomicReference<>();
        queryCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/companion_memories", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestMethod().equals("GET") && path.equals("/collections/companion_memories")) {
                respond(exchange, 200, "{\"result\":{\"status\":\"green\"}}");
            } else if (path.endsWith("/points/query")) {
                queryBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (queryCalls.incrementAndGet() == 1) {
                    respond(exchange, 503, "{\"status\":{\"error\":\"temporary\"}}");
                    return;
                }
                respond(exchange, 200, "{\"result\":{\"points\":[{\"id\":\"memory-1\",\"score\":0.9,\"payload\":{\"learner_id\":\"learner-a\",\"version\":2}}]}}");
            } else if (path.endsWith("/points/delete")) {
                respond(exchange, 404, "{\"status\":{\"error\":\"Not found\"}}");
            } else {
                respond(exchange, 200, "{\"result\":{\"status\":\"ok\"}}");
            }
        });
        server.start();
        CompanionMemoryProperties properties = new CompanionMemoryProperties();
        properties.setQdrantUrl("http://127.0.0.1:" + server.getAddress().getPort());
        store = new QdrantMemoryVectorStore(properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void queryUsesOwnerAndRelevantFiltersAndParsesQueryPoints() throws Exception {
        var matches = store.search(List.of(0.1f, 0.2f), "learner-a", 8, Duration.ofSeconds(2));

        assertThat(matches).containsExactly(new fit.iuh.engolearn.companion.service.MemoryVectorStore.VectorMatch("memory-1", "learner-a", 2));
        assertThat(queryCalls.get()).isEqualTo(2);
        var body = mapper.readTree(queryBody.get());
        assertThat(body.at("/filter/must/0/key").asText()).isEqualTo("learner_id");
        assertThat(body.at("/filter/must/0/match/value").asText()).isEqualTo("learner-a");
        assertThat(body.at("/filter/must/1/key").asText()).isEqualTo("retrieval_mode");
        assertThat(body.at("/filter/must/1/match/value").asText()).isEqualTo("RELEVANT");
    }

    @Test
    void deleteAgainstMissingCollectionIsAlreadyClean() {
        store.delete("memory-1", Duration.ofSeconds(2));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
