package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfDockerAvailable
class LearningPathIdHttpIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private JwtTokenService tokens;

    @Test
    void aNewlySubmittedAssessmentImmediatelySupportsRecommendationCreationAndTheResultLink() throws Exception {
        String owner = "pfc-demo-learner-v2";
        String attemptId = "native-submitted-assessment";
        seedAccount(owner);
        seedEligibleProjection(owner, attemptId);
        Document input = Document.parse(java.nio.file.Files.readString(java.nio.file.Path.of(
                "tools/mongo-json-importer/samples/assessment-paper-v2.json")));
        Document paper = input.get("collections", Document.class).getList("assessment_papers", Document.class).getFirst();
        paper.remove("_id");
        paper.put("paper_id", "native-submitted-paper");
        paper.put("demonstration", false);
        Document answers = new Document();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (Document unit : paper.getList("units", Document.class)) {
            for (Document question : unit.getList("questions", Document.class)) {
                String code = question.getString("skill_code");
                int position = counts.merge(code, 1, Integer::sum);
                int key = question.getInteger("correct_option");
                answers.put(question.getInteger("number").toString(), position <= (code.equals("R02") ? 2 : 5) ? key : (key + 1) % 4);
            }
        }
        String bindingHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("native-test-binding".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        mongoTemplate.getCollection("assessment_attempts").updateOne(new Document("_id", attemptId),
                new Document("$set", new Document("status", "ACTIVE").append("paper", paper).append("answers", answers)
                        .append("binding_hash", bindingHash).append("unit_index", 12).append("phase", "ANSWER")
                        .append("unit_started_at", Date.from(Instant.now())).append("started_at", Date.from(Instant.now()))
                        .append("next_due_at", Date.from(Instant.now().plusSeconds(1800))))
                        .append("$unset", new Document("skill_results", "").append("submitted_at", "").append("level", "")));
        mongoTemplate.getCollection("assessment_profiles").insertOne(new Document("user_id", owner)
                .append("initial_level", "A2").append("current_level", "A2").append("highest_level", "A2"));
        String base = "http://localhost:" + port;
        String recommendationUrl = base + "/api/learning-paths/recommendation/by-assessment?assessmentAttemptId=" + attemptId;
        assertThatThrownBy(() -> authenticatedGet(recommendationUrl, owner)).isInstanceOf(HttpClientErrorException.NotFound.class);

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode submitted = mapper.readTree(authenticatedRequest(base + "/api/v1/assessment/attempts/" + attemptId + "/submit",
                HttpMethod.POST, "{\"confirmed\":true}", owner)).path("data");
        assertThat(submitted.path("status").asString()).isEqualTo("READY");
        JsonNode recommendation = mapper.readTree(authenticatedGet(recommendationUrl, owner)).path("data");
        assertThat(recommendation.path("state").asString()).isEqualTo("AVAILABLE");
        String body = "{\"assessmentAttemptId\":\"" + attemptId + "\",\"microSkills\":[\"Reading for Gist\"]}";
        JsonNode created = mapper.readTree(authenticatedRequest(base + "/api/learning-paths/by-assessment", HttpMethod.POST, body, owner)).path("data");
        assertThat(created.path("created").asBoolean()).isTrue();
        JsonNode items = mapper.readTree(authenticatedGet(base + "/api/learning-paths", owner)).path("data").path("items");
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("sourceAssessmentAttemptId").asString()).isEqualTo(attemptId);
        JsonNode result = mapper.readTree(authenticatedGet(base + "/api/v1/assessment/attempts/" + attemptId + "/result", owner)).path("data");
        assertThat(result.path("id").asString()).isEqualTo(attemptId);
        assertThat(result.path("score").asInt()).isEqualTo(44);
        assertThat(mongoTemplate.collectionExists("attempts")).isFalse();
        assertThat(mongoTemplate.collectionExists("micro_skill_results")).isFalse();
    }

    @Test
    void concurrentCreationOfTheSameNativeAssessmentReturnsOnePath() throws Exception {
        String owner = "pfc-demo-learner-v2";
        seedAccount(owner);
        seedEligibleProjection(owner, "concurrent-native-id");
        var resolver = new org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver(
                mongoTemplate.getConverter().getMappingContext());
        resolver.resolveIndexFor(fit.iuh.engolearn.learningpath.model.LearningPath.class)
                .forEach(index -> mongoTemplate.indexOps(fit.iuh.engolearn.learningpath.model.LearningPath.class).ensureIndex(index));
        String url = "http://localhost:" + port + "/api/learning-paths/by-assessment";
        String body = "{\"assessmentAttemptId\":\"concurrent-native-id\",\"microSkills\":[\"Reading for Gist\"]}";
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> authenticatedRequest(url, HttpMethod.POST, body, owner));
            var two = executor.submit(() -> authenticatedRequest(url, HttpMethod.POST, body, owner));
            JsonMapper mapper = JsonMapper.builder().build();
            var first = mapper.readTree(one.get(20, java.util.concurrent.TimeUnit.SECONDS)).path("data");
            var second = mapper.readTree(two.get(20, java.util.concurrent.TimeUnit.SECONDS)).path("data");
            assertThat(first.path("pathId").asString()).isEqualTo(second.path("pathId").asString());
            assertThat(first.path("created").asBoolean() || second.path("created").asBoolean()).isTrue();
            assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isEqualTo(1);
        }
    }

    @Test
    void hexadecimalAssessmentIdsUseTheCanonicalIdForIdempotency() {
        String owner = "pfc-demo-learner-v2";
        String nativeId = new ObjectId().toHexString();
        seedAccount(owner);
        seedEligibleProjection(owner, nativeId);
        String url = "http://localhost:" + port + "/api/learning-paths/by-assessment";
        String body = "{\"assessmentAttemptId\":\"%s\",\"microSkills\":[\"Reading for Gist\"]}";
        JsonMapper mapper = JsonMapper.builder().build();
        var first = mapper.readTree(authenticatedRequest(url, HttpMethod.POST, body.formatted(nativeId.toUpperCase(java.util.Locale.ROOT)), owner)).path("data");
        var repeated = mapper.readTree(authenticatedRequest(url, HttpMethod.POST, body.formatted(nativeId), owner)).path("data");
        assertThat(first.path("created").asBoolean()).isTrue();
        assertThat(repeated.path("created").asBoolean()).isFalse();
        assertThat(first.path("pathId").asString()).isEqualTo(repeated.path("pathId").asString());
        assertThat(mongoTemplate.getCollection("learning_paths").find().first()
                .get("source", Document.class).getString("assessment_attempt_id")).isEqualTo(nativeId);
    }

    @Test
    void closedDayResultReturnsReviewOverHttp() {
        ObjectId id = new ObjectId();
        String userId = new ObjectId().toHexString();
        seedAccount(userId);
        List<Document> items = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("order", i)
                .append("question_id", "review-" + i).append("micro_skill", "Reading for Gist")).toList();
        Document day = new Document("day_no", 1).append("status", "CLOSED").append("close_reason", "SUBMITTED")
                .append("progress", new Document("assigned", 10).append("answered", 0).append("correct", 0))
                .append("result", new Document("wrong", 0).append("completion_rate", 0.0))
                .append("items", items);
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", id).append("user_id", userId)
                .append("status", "COMPLETED").append("is_active", false)
                .append("weeks", List.of(new Document("week_no", 1).append("status", "CLOSED").append("days", List.of(day)))));
        List<Document> options = List.of(new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"),
                new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        List<Document> snapshots = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("path_id", id)
                .append("user_id", userId).append("question_id", "review-" + i)
                .append("micro_skill", "Reading for Gist").append("skill", "READING")
                .append("prompt", "Question " + i).append("options", options)
                .append("correct_option_id", "A").append("explanation", "Explanation")).toList();
        mongoTemplate.getCollection("learning_path_questions").insertMany(snapshots);
        String url = "http://localhost:" + port + "/api/learning-paths/" + id.toHexString()
                + "/weeks/1/days/1/result";
        JsonNode data = JsonMapper.builder().build().readTree(authenticatedGet(url, userId)).path("data");
        assertThat(data.path("answered").asInt()).isZero();
        assertThat(data.path("status").asString()).isEqualTo("SUBMITTED");
        assertThat(data.path("practiceScore").isNull()).isTrue();
        assertThat(data.path("questions").size()).isEqualTo(10);
        assertThat(data.path("questions").get(0).path("answerStatus").asString()).isEqualTo("UNANSWERED");
        assertThat(data.path("questions").get(0).path("correctOptionId").asString()).isEqualTo("A");
        String detailUrl = "http://localhost:" + port + "/api/learning-paths/" + id.toHexString();
        assertThatThrownBy(() -> restTemplate.getForObject(detailUrl, String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        JsonNode detail = JsonMapper.builder().build().readTree(authenticatedGet(detailUrl, userId)).path("data");
        assertThat(detail.path("weeks").get(0).path("days").get(0).path("status").asString()).isEqualTo("SUBMITTED");

        String otherUserId = new ObjectId().toHexString();
        seedAccount(otherUserId);
        assertThatThrownBy(() -> authenticatedGet(detailUrl + "?userId=" + userId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(url + "?userId=" + userId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pfc-demo-learner-v2", "6aeea4550ca583c5809979b7"})
    void existingObjectIdIsReturnedUnchangedByListSearchAndDetail(String userId) {
        ObjectId id = new ObjectId();
        seedAccount(userId);
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", id)
                .append("user_id", userId)
                .append("created_at", Date.from(Instant.parse("2026-10-27T03:00:00Z")))
                .append("created_date", "2026-10-27")
                .append("status", "NOT_STARTED")
                .append("is_active", false)
                .append("level", "A1")
                .append("k", 1));
        String base = "http://localhost:" + port + "/api/learning-paths";
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode list = mapper.readTree(authenticatedGet(base + "?status=ACTIVE", userId));
        JsonNode search = mapper.readTree(authenticatedGet(
                base + "/search?keyword=27/10/2026", userId));
        JsonNode detail = mapper.readTree(authenticatedGet(
                base + "/" + id.toHexString(), userId));

        assertThat(list.path("data").path("items").get(0).path("id").asString()).isEqualTo(id.toHexString());
        assertThat(search.path("data").path("items").get(0).path("id").asString()).isEqualTo(id.toHexString());
        assertThat(detail.path("data").path("id").asString()).isEqualTo(id.toHexString());
        assertThat(mongoTemplate.getCollection("learning_paths").find(new Document("_id", id)).first()
                .get("_id")).isEqualTo(id);

        String otherUserId = new ObjectId().toHexString();
        seedAccount(otherUserId);
        for (String url : List.of(base + "?status=ACTIVE&userId=" + userId,
                base + "/search?keyword=27/10/2026&userId=" + userId)) {
            JsonNode otherList = mapper.readTree(authenticatedGet(url, otherUserId)).path("data");
            assertThat(otherList.path("items").size()).isZero();
            assertThat(otherList.path("counts").path("active").asInt()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void otherAccountsCannotReadOrChangeAnOwnersPracticeDay(UserRole role) {
        String ownerId = "pfc-demo-learner-v2";
        String otherUserId = new ObjectId().toHexString();
        ObjectId pathId = new ObjectId();
        seedAccount(ownerId);
        seedAccount(otherUserId, role);
        seedPracticeDay(pathId, ownerId);
        String dayUrl = "http://localhost:" + port + "/api/learning-paths/" + pathId + "/weeks/1/days/1";
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode started = mapper.readTree(authenticatedRequest(
                dayUrl + "/start", HttpMethod.POST, null, ownerId)).path("data");
        assertThat(started.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(started.path("questions").size()).isEqualTo(10);
        authenticatedRequest(dayUrl + "/questions/auth-q-1/draft", HttpMethod.PUT,
                "{\"selectedOptionId\":\"A\"}", ownerId);
        Document before = mongoTemplate.getCollection("learning_paths").find(new Document("_id", pathId)).first();

        String spoofedOwner = "?userId=" + ownerId;
        assertThatThrownBy(() -> authenticatedRequest(
                dayUrl + "/start" + spoofedOwner, HttpMethod.POST, null, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedRequest(
                dayUrl + "/questions/auth-q-1/draft" + spoofedOwner, HttpMethod.PUT,
                "{\"selectedOptionId\":\"B\"}", otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedRequest(
                dayUrl + "/submit" + spoofedOwner, HttpMethod.POST, null, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(dayUrl + "/result" + spoofedOwner, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThat(mongoTemplate.getCollection("learning_paths").find(new Document("_id", pathId)).first())
                .isEqualTo(before);
    }

    @Test
    void anotherUsersPeriodicTestCannotBeRecommendedOrUsedToCreateAPath() {
        String ownerId = new ObjectId().toHexString();
        String otherUserId = new ObjectId().toHexString();
        String attemptId = "owned-assessment-id";
        seedAccount(ownerId);
        seedAccount(otherUserId);
        seedPeriodicTest(attemptId, ownerId);
        String base = "http://localhost:" + port + "/api/learning-paths";
        String recommendation = base + "/recommendation?attemptId=" + attemptId;
        String body = """
                {"attemptId":"%s","microSkills":["Reading for Gist"]}
                """.formatted(attemptId);
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(authenticatedGet(recommendation, ownerId))
                .path("data").path("state").asString()).isEqualTo("NO_WEAK_SKILLS");

        assertThatThrownBy(() -> authenticatedGet(recommendation + "&userId=" + ownerId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedRequest(base + "?userId=" + ownerId,
                HttpMethod.POST, body, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();

        ObjectId pathId = new ObjectId();
        Document existing = new Document("_id", pathId).append("user_id", ownerId)
                .append("source", new Document("assessment_attempt_id", attemptId))
                .append("status", "COMPLETED").append("is_active", false);
        mongoTemplate.getCollection("learning_paths").insertOne(existing);
        JsonNode ownedPath = mapper.readTree(authenticatedRequest(base, HttpMethod.POST, body, ownerId)).path("data");
        assertThat(ownedPath.path("pathId").asString()).isEqualTo(pathId.toHexString());
        assertThat(ownedPath.path("created").asBoolean()).isFalse();

        assertThatThrownBy(() -> authenticatedGet(recommendation + "&userId=" + ownerId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedRequest(base + "?userId=" + ownerId,
                HttpMethod.POST, body, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_paths").find(new Document("_id", pathId)).first())
                .isEqualTo(existing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pfc-demo-learner-v2", "6aeea4550ca583c5809979b7"})
    void nativeAssessmentRecommendationResolvesOnlyTheOwnersCompletedResultWithoutWrites(String ownerId) {
        String otherId = new ObjectId().toHexString();
        String nativeId = "assessment-" + UUID.randomUUID();
        seedAccount(ownerId);
        seedAccount(otherId);
        seedEligibleProjection(ownerId, nativeId);
        Document before = mongoTemplate.getCollection("assessment_attempts").find(new Document("_id", nativeId)).first();
        String base = "http://localhost:" + port + "/api/learning-paths/recommendation";
        String url = base + "/by-assessment?assessmentAttemptId=" + nativeId;
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode resolved = mapper.readTree(authenticatedGet(url, ownerId)).path("data");
        assertThat(resolved).isEqualTo(mapper.readTree(authenticatedGet(base + "?attemptId=" + nativeId, ownerId)).path("data"));
        assertThat(resolved.path("state").asString()).isEqualTo("AVAILABLE");
        assertThat(resolved.path("microSkills").size()).isEqualTo(1);
        assertThat(resolved.path("microSkills").get(0).path("name").asString()).isEqualTo("Reading for Gist");
        assertThatThrownBy(() -> authenticatedGet(url + "&userId=" + ownerId, otherId)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment?assessmentAttemptId=missing", ownerId)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment", ownerId)).isInstanceOf(HttpClientErrorException.BadRequest.class);
        for (String invalid : List.of("", "x".repeat(101))) {
            assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment?assessmentAttemptId=" + invalid, ownerId))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
        assertThatThrownBy(() -> restTemplate.getForObject(url, String.class)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(mongoTemplate.getCollection("assessment_attempts").find(new Document("_id", nativeId)).first()).isEqualTo(before);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
        assertThat(mongoTemplate.collectionExists("attempts")).isFalse();
        assertThat(mongoTemplate.collectionExists("micro_skill_results")).isFalse();
    }

    @Test
    void nativeAssessmentRecommendationRejectsUnfinishedOrInvalidResults() {
        String ownerId = "pfc-demo-learner-v2";
        seedAccount(ownerId);
        seedPeriodicTest("native-id", ownerId);
        String url = "http://localhost:" + port + "/api/learning-paths/recommendation/by-assessment?assessmentAttemptId=native-id";
        for (String status : List.of("ACTIVE", "NO_RESULT", "ABANDONED")) {
            mongoTemplate.getCollection("assessment_attempts").updateOne(new Document("_id", "native-id"),
                    new Document("$set", new Document("status", status)));
            assertThatThrownBy(() -> authenticatedGet(url, ownerId)).isInstanceOf(HttpClientErrorException.NotFound.class);
        }
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pfc-demo-learner-v2", "6aeea4550ca583c5809979b7"})
    void nativeAssessmentCreationReturnsTheOwnedPathAndOpensWeekOneWithoutDuplicatingIt(String ownerId) {
        String otherId = new ObjectId().toHexString();
        seedAccount(ownerId);
        seedAccount(otherId);
        String attemptId = seedEligibleProjection(ownerId, "native-create-id");
        Document sourceBefore = mongoTemplate.getCollection("assessment_attempts").find(new Document("_id", attemptId)).first();
        String base = "http://localhost:" + port + "/api/learning-paths";
        String url = base + "/by-assessment";
        String body = "{\"assessmentAttemptId\":\"native-create-id\",\"microSkills\":[\"Reading for Gist\"]}";
        JsonMapper mapper = JsonMapper.builder().build();

        assertThatThrownBy(() -> authenticatedRequest(url + "?userId=" + ownerId, HttpMethod.POST, body, otherId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();

        JsonNode created = mapper.readTree(authenticatedRequest(url, HttpMethod.POST, body, ownerId)).path("data");
        String pathId = created.path("pathId").asString();
        assertThat(created.path("created").asBoolean()).isTrue();
        JsonNode detail = mapper.readTree(authenticatedGet(base + "/" + pathId, ownerId)).path("data");
        assertThat(detail.path("currentWeekNo").asInt()).isEqualTo(1);
        assertThat(detail.path("weeks").size()).isEqualTo(3);
        assertThat(detail.path("weeks").get(0).path("status").asString()).isEqualTo("OPEN");
        assertThat(detail.path("weeks").get(1).path("status").asString()).isEqualTo("LOCKED");
        assertThat(detail.path("progress").path("assigned").asInt()).isEqualTo(30);
        assertThat(mongoTemplate.getCollection("learning_paths")
                .find(new Document("_id", new ObjectId(pathId))).first().getString("user_id")).isEqualTo(ownerId);
        assertThat(mongoTemplate.getCollection("learning_path_questions")
                .countDocuments(new Document("path_id", new ObjectId(pathId)).append("user_id", ownerId))).isEqualTo(30);
        assertThat(mongoTemplate.getCollection("learning_path_questions")
                .distinct("question_id", String.class).into(new java.util.ArrayList<>())).hasSize(30);

        JsonNode repeated = mapper.readTree(authenticatedRequest(url, HttpMethod.POST, body, ownerId)).path("data");
        assertThat(repeated.path("created").asBoolean()).isFalse();
        assertThat(repeated.path("pathId").asString()).isEqualTo(pathId);
        assertThat(mapper.readTree(authenticatedGet(base
                + "/recommendation/by-assessment?assessmentAttemptId=native-create-id", ownerId))
                .path("data").path("state").asString()).isEqualTo("ALREADY_CREATED");
        assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, body, otherId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/" + pathId, otherId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        String dayUrl = base + "/" + pathId + "/weeks/1/days/1";
        JsonNode started = mapper.readTree(authenticatedRequest(
                dayUrl + "/start", HttpMethod.POST, null, ownerId)).path("data");
        assertThat(started.path("questions").size()).isEqualTo(10);
        String questionId = started.path("questions").get(0).path("questionId").asString();
        authenticatedRequest(dayUrl + "/questions/" + questionId + "/draft", HttpMethod.PUT,
                "{\"selectedOptionId\":\"A\"}", ownerId);
        JsonNode resumed = mapper.readTree(authenticatedRequest(
                dayUrl + "/start", HttpMethod.POST, null, ownerId)).path("data");
        assertThat(resumed.path("questions").get(0).path("selectedOptionId").asString()).isEqualTo("A");
        JsonNode submitted = mapper.readTree(authenticatedRequest(
                dayUrl + "/submit", HttpMethod.POST, null, ownerId)).path("data");
        assertThat(submitted.path("answered").asInt()).isEqualTo(1);
        assertThat(submitted.path("correct").asInt()).isEqualTo(1);
        JsonNode result = mapper.readTree(authenticatedGet(dayUrl + "/result", ownerId)).path("data");
        assertThat(result.path("status").asString()).isEqualTo("SUBMITTED");
        assertThat(result.path("questions").size()).isEqualTo(10);
        assertThat(result.path("correct").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> authenticatedGet(dayUrl + "/result", otherId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isEqualTo(30);
        assertThat(mongoTemplate.getCollection("assessment_attempts").find(new Document("_id", attemptId)).first()).isEqualTo(sourceBefore);
    }

    @Test
    void nativeAssessmentCreationRejectsStaleConfirmationWithoutWritingAPath() {
        String ownerId = new ObjectId().toHexString();
        seedAccount(ownerId);
        seedEligibleProjection(ownerId, "native-conflict-id");
        String url = "http://localhost:" + port + "/api/learning-paths/by-assessment";
        String wrongSkills = "{\"assessmentAttemptId\":\"native-conflict-id\",\"microSkills\":[\"Listening for Gist\"]}";
        assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, wrongSkills, ownerId))
                .isInstanceOfSatisfying(HttpClientErrorException.Conflict.class, exception -> {
                    JsonNode recommendation = JsonMapper.builder().build().readTree(exception.getResponseBodyAsString())
                            .path("recommendation");
                    assertThat(recommendation.path("state").asString()).isEqualTo("AVAILABLE");
                    assertThat(recommendation.path("microSkills").get(0).path("name").asString()).isEqualTo("Reading for Gist");
                });
        mongoTemplate.getCollection("question_pool").deleteOne(new Document("question_id", "native-pool-0"));
        String oldConfirmation = "{\"assessmentAttemptId\":\"native-conflict-id\",\"microSkills\":[\"Reading for Gist\"]}";
        assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, oldConfirmation, ownerId))
                .isInstanceOfSatisfying(HttpClientErrorException.Conflict.class, exception ->
                        assertThat(JsonMapper.builder().build().readTree(exception.getResponseBodyAsString())
                                .path("recommendation").path("state").asString()).isEqualTo("NO_ELIGIBLE_SKILLS"));
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
    }

    @Test
    void nativeAssessmentCreationValidatesTheBodyAndRequiresACompletedResult() {
        String ownerId = new ObjectId().toHexString();
        seedAccount(ownerId);
        String attemptId = seedEligibleProjection(ownerId, "native-validation-id");
        String url = "http://localhost:" + port + "/api/learning-paths/by-assessment";
        for (String body : List.of("{}",
                "{\"assessmentAttemptId\":\" \",\"microSkills\":[\"Reading for Gist\"]}",
                "{\"assessmentAttemptId\":\"" + "x".repeat(101) + "\",\"microSkills\":[\"Reading for Gist\"]}",
                "{\"assessmentAttemptId\":\"native-validation-id\",\"microSkills\":[]}",
                "{\"assessmentAttemptId\":\"native-validation-id\",\"microSkills\":[\"\"]}",
                "{\"assessmentAttemptId\":\"native-validation-id\",\"microSkills\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}")) {
            assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, body, ownerId))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
        String missing = "{\"assessmentAttemptId\":\"unknown-id\",\"microSkills\":[\"Reading for Gist\"]}";
        assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, missing, ownerId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        String valid = "{\"assessmentAttemptId\":\"native-validation-id\",\"microSkills\":[\"Reading for Gist\"]}";
        for (Document changed : List.of(new Document("status", "ACTIVE"),
                new Document("status", "ABANDONED"))) {
            mongoTemplate.getCollection("assessment_attempts").updateOne(new Document("_id", attemptId), new Document("$set", changed));
            assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, valid, ownerId))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
    }

    @Test
    void listAndSearchExposeOnlyOwnedCompletedAssessmentMappingsAndPreserveSourceIds() {
        String ownerId = "pfc-demo-learner-v2";
        String otherId = new ObjectId().toHexString();
        seedAccount(ownerId);
        seedAccount(otherId);
        mongoTemplate.getCollection("assessment_attempts").insertMany(List.of(
                assessmentProjection("native-linked-id", ownerId, "READY"),
                assessmentProjection("foreign-id", otherId, "READY"),
                assessmentProjection("abandoned-id", ownerId, "ABANDONED"),
                assessmentProjection("active-id", ownerId, "ACTIVE"),
                assessmentProjection("no-result-id", ownerId, "NO_RESULT")));
        List<String> sourceIds = List.of("native-linked-id", "missing-id", "foreign-id", " ", "abandoned-id", "active-id", "no-result-id");
        List<Document> pathDocuments = new ArrayList<>();
        for (int index = 0; index <= sourceIds.size(); index++) {
            Document path = new Document("_id", new ObjectId()).append("user_id", ownerId)
                    .append("status", "IN_PROGRESS").append("is_active", true)
                    .append("created_date", "2026-11-01")
                    .append("created_at", Date.from(Instant.parse("2026-11-01T03:00:00Z").plusSeconds(index)))
                    .append("micro_skills", List.of(new Document("micro_skill", "Reading for Gist")));
            if (index < sourceIds.size()) path.append("source", new Document("assessment_attempt_id", sourceIds.get(index)));
            pathDocuments.add(path);
        }
        mongoTemplate.getCollection("learning_paths").insertMany(pathDocuments);
        List<Document> sourcesBefore = mongoTemplate.getCollection("assessment_attempts").find().into(new ArrayList<>());
        String base = "http://localhost:" + port + "/api/learning-paths";
        JsonMapper mapper = JsonMapper.builder().build();
        for (String suffix : List.of("?status=ACTIVE", "/search?keyword=01/11/2026", "/search?keyword=Gist")) {
            JsonNode items = mapper.readTree(authenticatedGet(base + suffix, ownerId)).path("data").path("items");
            assertThat(items.size()).isEqualTo(pathDocuments.size());
            int linkedCount = 0;
            for (JsonNode item : items) {
                assertThat(item.has("sourceAttemptId")).isFalse();
                if (item.path("id").asString().equals(pathDocuments.getFirst().getObjectId("_id").toHexString())) {
                    assertThat(item.path("sourceAssessmentAttemptId").asString()).isEqualTo("native-linked-id");
                    linkedCount++;
                } else assertThat(item.path("sourceAssessmentAttemptId").isNull()).isTrue();
            }
            assertThat(linkedCount).isEqualTo(1);
            assertThat(mapper.readTree(authenticatedGet(base + suffix + "&userId=" + ownerId, otherId))
                    .path("data").path("items").size()).isZero();
        }
        assertThat(mongoTemplate.getCollection("learning_paths").find().into(new ArrayList<>())).containsExactlyElementsOf(pathDocuments);
        assertThat(mongoTemplate.getCollection("assessment_attempts").find().into(new ArrayList<>())).containsExactlyElementsOf(sourcesBefore);
    }

    private Document assessmentProjection(String attemptId, String userId, String status) {
        return new Document("_id", ObjectId.isValid(attemptId) ? new ObjectId(attemptId) : attemptId).append("user_id", userId).append("status", status).append("version", 0L)
                .append("paper", new Document("paper_id", "native-paper").append("series", "NATIVE").append("version", 2).append("demonstration", false));
    }

    private String seedEligibleProjection(String userId, String nativeId) {
        seedPeriodicTest(nativeId, userId);
        mongoTemplate.getCollection("assessment_attempts").updateOne(new Document("_id", ObjectId.isValid(nativeId) ? new ObjectId(nativeId) : nativeId),
                new Document("$set", new Document("skill_results.0.correct", 2).append("skill_results.0.percentage", 20)
                        .append("skill_results.0.band", "NEEDS_PRACTICE").append("correct_count", 22).append("reading_correct", 12)));
        List<Document> options = List.of(new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"), new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        mongoTemplate.getCollection("question_pool").insertMany(IntStream.range(0, 30).mapToObj(i ->
                new Document("question_id", "native-pool-" + i).append("micro_skill", "Reading for Gist")
                        .append("skill", "READING").append("level", "A2").append("status", "ACTIVE")
                        .append("prompt", "Question " + i).append("options", options)
                        .append("correct_option_id", "A").append("explanation", "Explanation")).toList());
        return nativeId;
    }

    private void seedPracticeDay(ObjectId pathId, String userId) {
        Date deadline = Date.from(Instant.now().plusSeconds(86400));
        List<Document> items = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("order", i)
                .append("question_id", "auth-q-" + i).append("micro_skill", "Reading for Gist")).toList();
        Document day = new Document("day_no", 1).append("status", "NOT_STARTED")
                .append("progress", new Document("assigned", 10).append("answered", 0).append("correct", 0))
                .append("items", items);
        Document week = new Document("week_no", 1).append("status", "OPEN")
                .append("close_at", deadline).append("days", List.of(day));
        mongoTemplate.getCollection("learning_paths").insertOne(new Document("_id", pathId)
                .append("user_id", userId).append("status", "NOT_STARTED").append("is_active", true)
                .append("config", new Document("questions_per_day", 10))
                .append("inactivity", new Document("cancel_at", deadline))
                .append("next_run_at", deadline).append("weeks", List.of(week)));
        List<Document> options = List.of(new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"),
                new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        List<Document> snapshots = IntStream.rangeClosed(1, 10).mapToObj(i -> new Document("path_id", pathId)
                .append("user_id", userId).append("question_id", "auth-q-" + i)
                .append("micro_skill", "Reading for Gist").append("skill", "READING")
                .append("prompt", "Question " + i).append("options", options)
                .append("correct_option_id", "A").append("explanation", "Explanation")).toList();
        mongoTemplate.getCollection("learning_path_questions").insertMany(snapshots);
    }

    private void seedPeriodicTest(String attemptId, String userId) {
        List<Document> results = List.of("R02", "R01", "R04", "L01", "L03").stream().map(code ->
                new Document("code", code).append("name", code).append("example", "Example")
                        .append("section", code.startsWith("L") ? "LISTENING" : "READING")
                        .append("correct", 5).append("total", 10).append("percentage", 50).append("band", "MONITOR")).toList();
        mongoTemplate.getCollection("assessment_attempts").insertOne(assessmentProjection(attemptId, userId, "READY")
                .append("submitted_at", Date.from(Instant.now())).append("level", "A2")
                .append("correct_count", 25).append("reading_correct", 15).append("listening_correct", 10)
                .append("analysis_grants", List.of("retained-test-grant")).append("skill_results", results));
    }

    private void seedAccount(String userId) {
        seedAccount(userId, UserRole.USER);
    }

    private void seedAccount(String userId, UserRole role) {
        mongoTemplate.getCollection("users").insertOne(new Document("_id", ObjectId.isValid(userId) ? new ObjectId(userId) : userId)
                .append("email", userId + "@example.com")
                .append("password_hash", "unused")
                .append("role", role.name())
                .append("locked", false));
    }

    private String authenticatedGet(String url, String userId) {
        return authenticatedRequest(url, HttpMethod.GET, null, userId);
    }

    private String authenticatedRequest(String url, HttpMethod method, String body, String userId) {
        String sessionId = UUID.randomUUID().toString();
        mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", sessionId)
                .append("account_id", userId)
                .append("expires_at", Date.from(Instant.now().plusSeconds(3600)))
                .append("revoked", false));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Assessment-Binding", "native-test-binding");
        headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN="
                + tokens.create(userId, sessionId, "access", 900));
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody();
    }
}
