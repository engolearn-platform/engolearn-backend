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
        "engolearn.placement.persistence.mode=mongo", "engolearn.speaking.enabled=false", "engolearn.speaking.manager.enabled=false",
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
    @Autowired private PlacementDeadlineService deadlines;
    @Autowired @Qualifier("mongoTransactionManager") private PlatformTransactionManager manager;
    @Autowired private fit.iuh.engolearn.grammar.service.GrammarAdminService grammarAdmin;
    @Autowired private fit.iuh.engolearn.placement.service.PlacementAdminQuestionService placementAdmin;
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

    @Test
    void staleValidatedSubmissionCannotFinalizeAnswersClearedByAnotherTab() throws Exception {
        Browser user = register();
        String id = answeredAttempt(user.client());
        PlacementAttempt validated = mongoTemplate.findById(id, PlacementAttempt.class);
        for (var answer : validated.getAnswers()) {
            var body = new HashMap<String, Object>();
            body.put("questionId", answer.getQuestionId());
            body.put("selectedOptionId", null);
            request(user.client(), "PATCH", API + "/" + id + "/answers", body, 200);
        }
        // The caller checked answers on an older revision; its retry must recheck that precondition.
        assertThat(deadlines.submitBeforeDeadline(validated, java.time.Instant.now())).isNull();
        request(user.client(), "POST", API + "/" + id + "/submit", null, 422);
        assertThat(mongoTemplate.findById(id, PlacementAttempt.class).getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(viewer(user.client())).isEqualTo("A1");
    }

    @Test
    void realCookieRolesProtectPlacementAndGrammarWithInheritedAuthorities() throws Exception {
        var guest = HttpClient.newHttpClient();
        request(guest, "GET", "/api/v1/grammar/admin/topics", null, 401);
        request(guest, "GET", "/api/v1/placement/admin/questions", null, 401);
        Browser user = register();
        request(user.client(), "GET", "/api/v1/grammar/admin/topics", null, 403);
        request(user.client(), "GET", "/api/v1/placement/admin/questions", null, 403);
        var account = mongoTemplate.findById(user.id(), User.class);
        account.setRole(fit.iuh.engolearn.shared.enums.UserRole.CONTENT_MANAGER);
        mongoTemplate.save(account);
        request(user.client(), "GET", "/api/v1/grammar/admin/topics", null, 200);
        request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
        request(user.client(), "POST", API + "/not-an-attempt/learning", null, 404);
        request(user.client(), "GET", "/api/v1/placement/admin/questions", null, 403);
        account.setRole(fit.iuh.engolearn.shared.enums.UserRole.ADMIN);
        mongoTemplate.save(account);
        request(user.client(), "GET", "/api/v1/placement/admin/questions", null, 200);
        request(user.client(), "GET", "/api/v1/grammar/admin/topics", null, 200);
        request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
        request(user.client(), "POST", API + "/not-an-attempt/learning", null, 404);
        request(user.client(), "GET", "/api/v1/placement/admin/questions/media/preflight", null, 200);
        account.setRole(fit.iuh.engolearn.shared.enums.UserRole.USER);
        mongoTemplate.save(account);
        request(user.client(), "GET", "/api/v1/grammar/admin/topics", null, 403);
        request(user.client(), "GET", "/api/v1/placement/admin/questions/media/preflight", null, 403);
    }

    @Test
    void springServiceProxiesRejectMissingAuthenticationBeforeReadingContent() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(grammarAdmin)).isTrue();
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(placementAdmin)).isTrue();
        assertThatThrownBy(() -> grammarAdmin.list(null, null, null, 0, 20))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> placementAdmin.search("", "", "", "", 0, 20))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void grammarUsesRealPlacementLevelForDeepLinksWithoutCompletingLessons() throws Exception {
        Browser user = register();
        JsonNode initial = request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
        assertThat(initial.path("currentLevel").asText()).isEqualTo("A1");
        assertThat(initial.path("levels").get(1).path("unlocked").asBoolean()).isFalse();
        String topicId = publishedCanonicalA2Topic(user);
        request(user.client(), "GET", "/api/v1/grammar/topics/" + topicId, null, 403);
        String attempt = answeredAttempt(user.client());
        request(user.client(), "POST", API + "/" + attempt + "/submit", null, 200);
        assertThat(viewer(user.client())).isEqualTo("A2");
        // Open a deep link before roadmap has persisted the Placement-based grant.
        JsonNode topic = request(user.client(), "GET", "/api/v1/grammar/topics/" + topicId, null, 200);
        assertThat(topic.path("topic").path("completedLessons").asInt()).isZero();
        assertThat(topic.path("lessons").get(0).path("unlocked").asBoolean()).isTrue();
        assertThat(topic.path("lessons").get(1).path("unlocked").asBoolean()).isFalse();
        JsonNode roadmap = request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
        assertThat(roadmap.path("currentLevel").asText()).isEqualTo("A2");
        assertThat(roadmap.path("levels").get(1).path("unlocked").asBoolean()).isTrue();
        assertThat(roadmap.path("levels").get(2).path("unlocked").asBoolean()).isFalse();
        assertThat(roadmap.path("newlyUnlockedLevels").toString()).contains("A2");
        var grant = mongoTemplate.getCollection("grammar_level_unlocks")
                .find(new Document("user_id", user.id()).append("level", "A2")).first();
        assertThat(grant).isNotNull();
        assertThat(grant.getString("reason")).isEqualTo("PLACEMENT");
        assertThat(request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200)
                .path("newlyUnlockedLevels").isEmpty()).isTrue();
        assertThat(mongoTemplate.getCollection("grammar_lesson_progress")
                .countDocuments(new Document("user_id", user.id()))).isZero();
        assertThat(viewer(user.client())).isEqualTo("A2");
    }

    @Test
    void grammarCapsAdvancedProfileAtB2WithoutChangingUserLevel() throws Exception {
        Browser user = register();
        transactions.execute(() -> promotion.promote(user.id(), CefrLevel.C1));
        JsonNode roadmap = request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200);
        assertThat(roadmap.path("currentLevel").asText()).isEqualTo("B2");
        for (JsonNode level : roadmap.path("levels")) {
            assertThat(level.path("unlocked").asBoolean()).isTrue();
        }
        assertThat(viewer(user.client())).isEqualTo("C1");
        transactions.execute(() -> promotion.promote(user.id(), CefrLevel.C2));
        assertThat(request(user.client(), "GET", "/api/v1/grammar/roadmap", null, 200)
                .path("newlyUnlockedLevels").isEmpty()).isTrue();
        assertThat(viewer(user.client())).isEqualTo("C2");
    }

    private String publishedCanonicalA2Topic(Browser user) throws Exception {
        Document collections = Document.parse(Files.readString(Path.of(
                "tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json")))
                .get("collections", Document.class);
        Document topic = collections.getList("grammar_topics", Document.class).stream()
                .filter(t -> "A2".equals(t.getString("level"))).findFirst().orElseThrow();
        String topicId = topic.getString("_id");
        mongoTemplate.getCollection("grammar_topics").insertOne(topic);
        var lessons = collections.getList("grammar_lessons", Document.class).stream()
                .filter(l -> topicId.equals(l.getString("topic_id"))).toList();
        mongoTemplate.getCollection("grammar_lessons").insertMany(lessons);
        var account = mongoTemplate.findById(user.id(), User.class);
        account.setRole(fit.iuh.engolearn.shared.enums.UserRole.ADMIN);
        mongoTemplate.save(account);
        String path = "/api/v1/grammar/admin/topics/" + topicId;
        for (Document lesson : lessons) {
            String lessonPath = path + "/lessons/" + lesson.getString("_id");
            JsonNode current = request(user.client(), "GET", lessonPath, null, 200);
            request(user.client(), "POST", lessonPath + "/ready",
                    Map.of("version", current.path("version").asLong()), 200);
        }
        JsonNode current = request(user.client(), "GET", path, null, 200);
        request(user.client(), "POST", path + "/publish", Map.of("version", current.path("version").asLong()), 200);
        account.setRole(fit.iuh.engolearn.shared.enums.UserRole.USER);
        mongoTemplate.save(account);
        return topicId;
    }

    @Test
    void inheritedLearnerRolesCanSubmitAndReadOnlyTheirOwnPlacement() throws Exception {
        var anonymous = HttpClient.newHttpClient();
        for (var role : List.of(fit.iuh.engolearn.shared.enums.UserRole.CONTENT_MANAGER,
                fit.iuh.engolearn.shared.enums.UserRole.ADMIN)) {
            Browser learner = register();
            var account = mongoTemplate.findById(learner.id(), User.class);
            account.setRole(role);
            mongoTemplate.save(account);
            String id = answeredAttempt(learner.client());
            request(learner.client(), "POST", API + "/" + id + "/submit", null, 200);
            assertThat(viewer(learner.client())).isEqualTo("A2");
            assertThat(request(learner.client(), "GET", API + "/" + id + "/result", null, 200)
                    .path("placementLevel").asText()).isEqualTo("A2");
            request(learner.client(), "GET", API + "/" + id + "/review", null, 200);
            request(learner.client(), "POST", API + "/" + id + "/learning", null, 200);
            request(anonymous, "GET", API + "/" + id + "/result", null, 401);
            Browser foreign = register();
            request(foreign.client(), "GET", API + "/" + id + "/result", null, 404);
            // Even ADMIN inherits learner capability, never another account's attempt ownership.
            var foreignAccount = mongoTemplate.findById(foreign.id(), User.class);
            foreignAccount.setRole(fit.iuh.engolearn.shared.enums.UserRole.ADMIN);
            mongoTemplate.save(foreignAccount);
            request(foreign.client(), "GET", API + "/" + id + "/review", null, 404);
        }
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
