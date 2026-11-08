package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.MongoCollectionInspector;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelLookup;
import fit.iuh.engolearn.user.contract.UserLevelPromotion;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "engolearn.placement.persistence.mode=mongo",
        "engolearn.placement.test-set.allow-draft=true",
        "engolearn.security.dev-auth.enabled=false",
        "engolearn.security.jwt.secret=placement-level-real-http-integration-secret-32-bytes",
        "engolearn.subscription.activation-delay-ms=3600000"
})
class PlacementUserLevelIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String API = "/api/v1/placement/attempts";
    @Autowired private ObjectMapper json;
    @Autowired private MongoMappingContext mapping;
    @Autowired private UserLevelPromotion promotion;
    @Autowired private UserLevelLookup levels;
    @Autowired private PlacementTransactionExecutor transactions;
    @Autowired private PlacementAttemptService attempts;
    @Autowired @Qualifier("mongoTransactionManager") private PlatformTransactionManager manager;
    private Map<String, String> canonicalAnswers;

    @BeforeEach
    void realCollectionsAndPackagedPlacementInput() throws Exception {
        new MongoCollectionInspector(mongoTemplate, mapping).inspectAndCreateMissing();
        var indexes = new MongoPersistentEntityIndexResolver(mapping);
        for (Class<?> type : List.of(PlacementAttempt.class, PlacementTestSet.class, User.class)) {
            indexes.resolveIndexFor(type).forEach(index -> mongoTemplate.indexOps(type).createIndex(index));
        }
        // Actual shared importer input, unchanged DRAFT snapshot; no mock paper or fabricated API response.
        Document root = Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/placement-v3-test-sets-v2.importer.json")));
        var sets = root.get("collections", Document.class).getList("placement_test_sets", Document.class);
        mongoTemplate.getCollection("placement_test_sets").insertMany(sets);
        canonicalAnswers = new HashMap<>();
        sets.getFirst().getList("items", Document.class).forEach(q -> canonicalAnswers.put(q.getString("id"), q.getString("correct_option_id")));
    }

    @Test
    void higherC1AndC2SurviveHistoricalLearningAndRepeatedCommands() throws Exception {
        Browser user = register();
        String id = answeredAttempt(user.client());
        request(user.client(), "POST", API + "/" + id + "/submit", null, 200);
        assertThat(viewer(user.client())).isEqualTo("A2");
        var created = mongoTemplate.findById(user.id(), UserProfile.class).getCreatedAt();
        assertThat(transactions.execute(() -> promotion.promote(user.id(), CefrLevel.C1))).isEqualTo(CefrLevel.C1);
        var updated = mongoTemplate.findById(user.id(), UserProfile.class).getUpdatedAt();
        assertThat(request(user.client(), "POST", API + "/" + id + "/learning", null, 200).path("profileLevel").asText()).isEqualTo("C1");
        assertThat(viewer(user.client())).isEqualTo("C1");
        assertThat(mongoTemplate.findById(user.id(), UserProfile.class).getUpdatedAt()).isEqualTo(updated);
        assertThat(transactions.execute(() -> promotion.promote(user.id(), CefrLevel.C2))).isEqualTo(CefrLevel.C2);
        assertThat(request(user.client(), "POST", API + "/" + id + "/learning", null, 200).path("profileLevel").asText()).isEqualTo("C2");
        assertThat(mongoTemplate.findById(user.id(), UserProfile.class).getCreatedAt()).isEqualTo(created);
    }

    @Test
    void competingRealTransactionsRetryWithoutDowngradingProfile() throws Exception {
        Browser user = register();
        var barrier = new CyclicBarrier(2);
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var low = pool.submit(competing(user.id(), CefrLevel.A2, barrier, calls));
            var high = pool.submit(competing(user.id(), CefrLevel.C1, barrier, calls));
            assertThat(low.get(30, TimeUnit.SECONDS).ordinal()).isGreaterThanOrEqualTo(CefrLevel.A2.ordinal());
            assertThat(high.get(30, TimeUnit.SECONDS)).isEqualTo(CefrLevel.C1);
            assertThat(viewer(user.client())).isEqualTo("C1");
            assertThat(calls.get()).isGreaterThan(2);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void resultAndProfileRollBackTogetherThenCommitOnRealHttpSubmit() throws Exception {
        Browser user = register();
        String id = answeredAttempt(user.client());
        long revision = mongoTemplate.findById(id, PlacementAttempt.class).getRevision();
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
            attempts.submit(id, new PlacementAccessContext(user.id(), null));
            throw new IllegalStateException("force outer rollback");
        })).hasMessage("force outer rollback");
        assertThat(request(user.client(), "GET", API + "/" + id, null, 200).path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(mongoTemplate.findById(id, PlacementAttempt.class).getRevision()).isEqualTo(revision);
        assertThat(viewer(user.client())).isEqualTo("A1");
        request(user.client(), "POST", API + "/" + id + "/submit", null, 200);
        assertThat(viewer(user.client())).isEqualTo("A2");
    }

    @Test
    void learningCommandCannotSubmitAnInProgressAttempt() throws Exception {
        Browser user = register();
        String id = answeredAttempt(user.client());
        request(user.client(), "POST", API + "/" + id + "/learning", null, 404);
        assertThat(request(user.client(), "GET", API + "/" + id, null, 200).path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(viewer(user.client())).isEqualTo("A1");
    }

    private Callable<CefrLevel> competing(String userId, CefrLevel target, CyclicBarrier barrier, AtomicInteger calls) {
        AtomicBoolean first = new AtomicBoolean(true);
        return () -> transactions.execute(() -> {
            calls.incrementAndGet();
            if (first.getAndSet(false)) {
                assertThat(levels.findLevel(userId)).contains(CefrLevel.A1);
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }
            return promotion.promote(userId, target);
        });
    }

    private Browser register() throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        JsonNode user = request(client, "POST", "/api/v1/auth/register", Map.of("email", "level-it-" + UUID.randomUUID() + "@example.test", "password", "LevelCheck-Only-2026!"), 201);
        assertThat(viewer(client)).isEqualTo("A1");
        return new Browser(client, user.path("id").asText());
    }

    private String answeredAttempt(HttpClient client) throws Exception {
        String id = request(client, "POST", API, null, 201).path("attemptId").asText();
        JsonNode questions = request(client, "GET", API + "/" + id, null, 200).path("questions");
        for (int i = 0; i < 8; i++) {
            String q = questions.get(i).path("id").asText();
            request(client, "PATCH", API + "/" + id + "/answers", Map.of("questionId", q, "selectedOptionId", canonicalAnswers.get(q)), 200);
        }
        return id;
    }

    private String viewer(HttpClient client) throws Exception {
        return request(client, "GET", "/api/v1/topic-learning/viewer", null, 200).path("cefrLevel").asText();
    }

    private JsonNode request(HttpClient client, String method, String path, Object body, int expected) throws Exception {
        var payload = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").method(method, payload).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }

    private record Browser(HttpClient client, String id) {}
}
