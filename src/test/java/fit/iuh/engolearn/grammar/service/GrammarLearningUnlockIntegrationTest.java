package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.MongoCollectionInspector;
import fit.iuh.engolearn.grammar.model.GrammarLevelUnlock;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.user.model.User;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "engolearn.speaking.enabled=false", "engolearn.speaking.manager.enabled=false",
        "engolearn.security.dev-auth.enabled=false",
        "engolearn.security.jwt.secret=grammar-unlock-real-http-integration-secret-32-bytes",
        "engolearn.subscription.activation-delay-ms=3600000"
})
class GrammarLearningUnlockIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired private ObjectMapper json;
    @Autowired private MongoMappingContext mapping;
    private final Map<String, Document> lessonInputs = new HashMap<>();

    @Test
    void sixOfEightTopicsUnlockNextLevelAndExistingGrantSurvivesCatalogChanges() throws Exception {
        new MongoCollectionInspector(mongoTemplate, mapping).inspectAndCreateMissing();
        var indexes = new MongoPersistentEntityIndexResolver(mapping);
        for (Class<?> type : List.of(User.class, GrammarLevelUnlock.class)) {
            indexes.resolveIndexFor(type).forEach(index -> mongoTemplate.indexOps(type).createIndex(index));
        }
        Browser admin = register();
        User account = mongoTemplate.findById(admin.id(), User.class);
        account.setRole(UserRole.ADMIN);
        mongoTemplate.save(account);
        List<Document> topics = publishEightCanonicalTopics(admin.client());
        Browser learner = register();
        request(HttpClient.newHttpClient(), "GET", "/api/v1/grammar/roadmap", null, 401);
        request(learner.client(), "GET", "/api/v1/grammar/admin/topics", null, 403);
        assertThat(level(roadmap(learner), "A2").path("lockHint").asText()).contains("6 chủ đề");

        for (int i = 0; i < 6; i++) {
            String topicId = topics.get(i).getString("_id");
            JsonNode topic = request(learner.client(), "GET", "/api/v1/grammar/topics/" + topicId, null, 200);
            JsonNode lessons = topic.path("lessons");
            if (i == 0) {
                assertThat(lessons.size()).isGreaterThan(1);
                request(learner.client(), "GET", "/api/v1/grammar/topics/" + topicId + "/lessons/"
                        + lessons.get(1).path("id").asText(), null, 403);
            }
            for (int l = 0; l < lessons.size(); l++) {
                completeLesson(learner.client(), topicId, lessons.get(l).path("id").asText());
                if (i == 0 && l == 0) {
                    assertThat(level(roadmap(learner), "A1").path("completedTopics").asInt()).isZero();
                }
            }
            JsonNode state = roadmap(learner);
            assertThat(level(state, "A1").path("completedTopics").asInt()).isEqualTo(i + 1);
            assertThat(level(state, "A2").path("unlocked").asBoolean()).isEqualTo(i == 5);
            if (i == 4) assertThat(level(state, "A2").path("lockHint").asText()).contains("1 chủ đề");
        }
        for (int i = 0; i < 2; i++) {
            String path = "/api/v1/grammar/admin/topics/" + topics.get(i).getString("_id");
            JsonNode topic = request(admin.client(), "GET", path, null, 200);
            request(admin.client(), "POST", path + "/archive", Map.of("version", topic.path("version").asLong()), 200);
        }
        JsonNode state = roadmap(learner);
        assertThat(level(state, "A1").path("totalTopics").asInt()).isEqualTo(6);
        assertThat(level(state, "A1").path("completedTopics").asInt()).isEqualTo(4);
        assertThat(level(state, "A2").path("unlocked").asBoolean()).isTrue();
        assertThat(level(state, "B1").path("unlocked").asBoolean()).isFalse();
        assertThat(state.path("currentLevel").asText()).isEqualTo("A1");
        assertThat(request(learner.client(), "GET", "/api/v1/topic-learning/viewer", null, 200)
                .path("cefrLevel").asText()).isEqualTo("A1");
    }

    private List<Document> publishEightCanonicalTopics(HttpClient admin) throws Exception {
        Document collections = Document.parse(Files.readString(Path.of(
                "tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json")))
                .get("collections", Document.class);
        var topics = collections.getList("grammar_topics", Document.class).stream()
                .filter(t -> "A1".equals(t.getString("level")))
                .sorted(Comparator.comparing(t -> t.getInteger("order"))).limit(8).toList();
        assertThat(topics).hasSize(8);
        for (Document topic : topics) {
            String topicId = topic.getString("_id");
            mongoTemplate.getCollection("grammar_topics").insertOne(topic);
            var lessons = collections.getList("grammar_lessons", Document.class).stream()
                    .filter(l -> topicId.equals(l.getString("topic_id"))).toList();
            mongoTemplate.getCollection("grammar_lessons").insertMany(lessons);
            String path = "/api/v1/grammar/admin/topics/" + topicId;
            for (Document lesson : lessons) {
                lessonInputs.put(lesson.getString("_id"), lesson.get("draft", Document.class));
                String lessonPath = path + "/lessons/" + lesson.getString("_id");
                JsonNode current = request(admin, "GET", lessonPath, null, 200);
                request(admin, "POST", lessonPath + "/ready", Map.of("version", current.path("version").asLong()), 200);
            }
            JsonNode current = request(admin, "GET", path, null, 200);
            request(admin, "POST", path + "/publish", Map.of("version", current.path("version").asLong()), 200);
        }
        return topics;
    }

    private void completeLesson(HttpClient learner, String topicId, String lessonId) throws Exception {
        String path = "/api/v1/grammar/topics/" + topicId + "/lessons/" + lessonId;
        JsonNode theory = request(learner, "GET", path, null, 200);
        request(learner, "POST", path + "/seen", Map.of("contentVersion", theory.path("contentVersion").asInt()), 200);
        JsonNode session = request(learner, "POST", path + "/practice", Map.of("restart", false), 200);
        var questions = lessonInputs.get(lessonId).getList("questions", Document.class);
        for (int i = 0; i < 5; i++) {
            JsonNode question = session.path("question");
            Document original = questions.stream().filter(q -> q.getString("prompt").equals(question.path("prompt").asText()))
                    .findFirst().orElseThrow();
            String answer;
            if ("MULTIPLE_CHOICE".equals(original.getString("type"))) {
                String correct = original.getList("options", String.class).get(original.getInteger("correct_index"));
                answer = null;
                for (JsonNode option : question.path("options")) {
                    if (option.path("text").asText().equals(correct) == (i < 3)) { answer = option.path("id").asText(); break; }
                }
            } else answer = i < 3 ? original.getString("correct_answer") : "__intentionally_wrong__";
            session = request(learner, "POST", "/api/v1/grammar/practice/" + session.path("id").asText() + "/answers",
                    Map.of("requestId", UUID.randomUUID().toString(), "questionId", question.path("id").asText(), "answer", answer), 200)
                    .path("session");
        }
        assertThat(session.path("result").path("score").asInt()).isEqualTo(3);
        assertThat(session.path("result").path("completed").asBoolean()).isTrue();
    }

    private JsonNode roadmap(Browser user) throws Exception {
        return request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
    }
    private JsonNode level(JsonNode roadmap, String level) {
        for (JsonNode item : roadmap.path("levels")) if (level.equals(item.path("level").asText())) return item;
        throw new AssertionError("Missing level " + level);
    }
    private Browser register() throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        JsonNode user = request(client, "POST", "/api/v1/auth/register", Map.of("email", "grammar-unlock-" + UUID.randomUUID()
                + "@example.test", "password", "GrammarCheck-Only-2026!"), 201);
        return new Browser(client, user.path("id").asText());
    }
    private JsonNode request(HttpClient client, String method, String path, Object body, int expected) throws Exception {
        var payload = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
    private record Browser(HttpClient client, String id) {}
}
