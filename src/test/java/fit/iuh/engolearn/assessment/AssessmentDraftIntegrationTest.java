package fit.iuh.engolearn.assessment;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("mongoAvailable")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "engolearn.security.jwt.secret=isolated-assessment-draft-integration-tests-key",
        "engolearn.security.cookies.secure=false", "engolearn.messaging.enabled=false",
        "spring.mongodb.username=", "spring.mongodb.password="
})
class AssessmentDraftIntegrationTest {
    private static final String DATABASE = "engolearn_assessment_draft_it";
    private static MongoDBContainer container;
    @Autowired private MongoTemplate mongoTemplate;
    @LocalServerPort private int port;
    private static final String DISPOSABLE_MONGO = System.getenv("ASSESSMENT_DRAFT_TEST_MONGO_URI");

    static boolean mongoAvailable() {
        return DISPOSABLE_MONGO != null && !DISPOSABLE_MONGO.isBlank() || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void optionalReplicaSet(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", AssessmentDraftIntegrationTest::mongoUri);
        registry.add("spring.mongodb.database", () -> DATABASE);
    }

    private static synchronized String mongoUri() {
        if (DISPOSABLE_MONGO != null && !DISPOSABLE_MONGO.isBlank()) return DISPOSABLE_MONGO;
        if (container == null) {
            container = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble")).withReplicaSet();
            container.start();
        }
        return container.getReplicaSetUrl(DATABASE);
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) container.stop();
    }

    @BeforeEach
    void clearFixtures() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo(DATABASE);
        for (String name : mongoTemplate.getCollectionNames()) {
            mongoTemplate.getCollection(name).deleteMany(new Document());
        }
    }

    private static final String DRAFTS = "/api/v1/assessment/admin/drafts";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> call(String method, String path, String body, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = json.writeValueAsString(Map.of("email", email, "password", "Isolated-draft-123!"));
        assertThat(call("POST", "/api/v1/auth/register", body, null).statusCode()).isEqualTo(201);
        mongoTemplate.getCollection("users").updateOne(new Document("email", email),
                new Document("$set", new Document("role", role)));
        var response = call("POST", "/api/v1/auth/login", body, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream().map(v -> v.split(";", 2)[0])
                .filter(v -> v.startsWith("ACCESS_TOKEN=")).findFirst().orElseThrow();
    }

    @Test void onlyContentStaffCanOpenDrafts() throws Exception {
        assertThat(call("GET", DRAFTS, null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", DRAFTS, null, login("USER")).statusCode()).isEqualTo(403);
        assertThat(call("GET", DRAFTS, null, login("CONTENT_MANAGER")).statusCode()).isEqualTo(200);
        assertThat(call("GET", DRAFTS, null, login("ADMIN")).statusCode()).isEqualTo(200);
    }

    @Test void twoEditorsCannotOverwriteEachOtherOrDuplicateADraft() throws Exception {
        String cookie = login("CONTENT_MANAGER");
        String create = "{\"series\":\"F03-M1\",\"name\":\"\"}";
        var first = call("POST", DRAFTS, create, cookie);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(call("POST", DRAFTS, create, cookie).statusCode()).isEqualTo(409);
        String revision = json.readTree(first.body()).at("/data/revision").asString();
        String content = """
                {"revision":"%s","name":"%s","skills":[],"units":[{"kind":"READING_SINGLE",
                "questions":[{"prompt":"","options":[],"correctOption":null}]}]}
                """;
        String path = DRAFTS + "/F03-M1";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var go = new CountDownLatch(1);
            var one = pool.submit(() -> { go.await(); return call("PUT", path, content.formatted(revision, "Một"), cookie); });
            var two = pool.submit(() -> { go.await(); return call("PUT", path, content.formatted(revision, "Hai"), cookie); });
            go.countDown();
            var a = one.get(30, TimeUnit.SECONDS);
            var b = two.get(30, TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 409);
            assertThat(json.readTree((a.statusCode() == 409 ? a : b).body()).path("code").asString()).isEqualTo("STALE_DRAFT");
            var reopened = json.readTree(call("GET", path, null, cookie).body()).path("data");
            var winner = json.readTree((a.statusCode() == 200 ? a : b).body()).path("data");
            assertThat(reopened).isEqualTo(winner);
            assertThat(reopened.at("/units/0/questions/0/correctOption").isNumber()).isFalse();
        }
        assertThat(mongoTemplate.getCollection("assessment_papers").countDocuments()).isZero();
    }

    @Test void editingTheShippedExamPreservesItsSourceAndAudio() throws Exception {
        var sample = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var paper = sample.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        var published = mongoTemplate.getCollection("assessment_papers");
        published.insertOne(paper);
        var before = published.find().first();
        String series = paper.getString("series");
        String cookie = login("ADMIN");
        var created = call("POST", DRAFTS, json.writeValueAsString(Map.of("series", series, "name", "Bản tiếp theo")), cookie);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        var data = json.readTree(created.body()).path("data");
        assertThat(data.path("version").asInt()).isEqualTo(paper.getInteger("version") + 1);
        assertThat(data.path("units").size()).isEqualTo(26);
        var body = json.writeValueAsString(Map.of("revision", data.path("revision"), "name", "Tên mới",
                "units", data.path("units"), "skills", data.path("skills")));
        var saved = call("PUT", DRAFTS + "/" + series, body, cookie);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json.readTree(saved.body()).at("/data/units")).isEqualTo(data.path("units"));
        var persisted = mongoTemplate.getCollection("assessment_paper_drafts").find().first();
        assertThat(persisted.getList("units", Document.class).getFirst().getString("audio_sha256"))
                .isNotBlank().isEqualTo(paper.getList("units", Document.class).getFirst().getString("audio_sha256"));
        assertThat(published.find().first()).isEqualTo(before);
    }

    @Test
    void aReviewedRevisionPublishesOnlyOnceAndCanBeArchived() throws Exception {
        var fixture = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/assessment-demo.json")));
        var source = fixture.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        mongoTemplate.getCollection("assessment_papers").insertOne(source);
        String series = source.getString("series");
        String path = DRAFTS + "/" + series;
        String cookie = login("CONTENT_MANAGER");
        var created = call("POST", DRAFTS, json.writeValueAsString(Map.of("series", series, "name", "Phiên bản mới")), cookie);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String revision = json.readTree(created.body()).at("/data/revision").asString();
        String request = json.writeValueAsString(Map.of("revision", revision));

        assertThat(call("POST", path + "/publish", request, cookie).statusCode()).isEqualTo(409);
        var preview = call("POST", path + "/preview", request, cookie);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        var audio = call("GET", path + "/preview/audio/0?revision=" + revision, null, cookie);
        assertThat(audio.statusCode()).as(audio.body()).isEqualTo(200);
        assertThat(json.readTree(audio.body()).at("/data/base64").asString()).isNotBlank();
        var reviewed = call("POST", path + "/review", request, cookie);
        assertThat(reviewed.statusCode()).as(reviewed.body()).isEqualTo(204);
        var released = call("POST", path + "/publish", request, cookie);
        assertThat(released.statusCode()).as(released.body()).isEqualTo(200);
        var data = json.readTree(released.body()).path("data");
        var replay = call("POST", path + "/publish", request, cookie);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("data")).isEqualTo(data);
        var papers = mongoTemplate.getCollection("assessment_papers");
        assertThat(papers.countDocuments(new Document("series", series))).isEqualTo(2);
        assertThat(papers.countDocuments(new Document("series", series).append("published", true))).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("assessment_attempts").countDocuments()).isZero();

        String archivePath = "/api/v1/assessment/admin/papers/" + series + "/" + data.path("version").asInt() + "/archive";
        assertThat(call("POST", archivePath, "{}", login("USER")).statusCode()).isEqualTo(403);
        assertThat(call("POST", archivePath, "{}", cookie).statusCode()).isEqualTo(200);
        assertThat(call("POST", archivePath, "{}", cookie).statusCode()).isEqualTo(200);
        assertThat(papers.countDocuments(new Document("series", series).append("published", true))).isZero();
        assertThat(papers.countDocuments(new Document("series", series))).isEqualTo(2);
    }
}
