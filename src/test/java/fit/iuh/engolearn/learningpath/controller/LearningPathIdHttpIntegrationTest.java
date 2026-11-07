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
    void closedDayResultReturnsReviewOverHttp() {
        ObjectId id = new ObjectId();
        ObjectId userId = new ObjectId();
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

        ObjectId otherUserId = new ObjectId();
        seedAccount(otherUserId);
        assertThatThrownBy(() -> authenticatedGet(detailUrl + "?userId=" + userId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(url + "?userId=" + userId, otherUserId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void existingObjectIdIsReturnedUnchangedByListSearchAndDetail() {
        ObjectId id = new ObjectId();
        ObjectId userId = new ObjectId();
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

        ObjectId otherUserId = new ObjectId();
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
        ObjectId ownerId = new ObjectId();
        ObjectId otherUserId = new ObjectId();
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
        ObjectId ownerId = new ObjectId();
        ObjectId otherUserId = new ObjectId();
        ObjectId attemptId = new ObjectId();
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
                .append("source", new Document("attempt_id", attemptId))
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

    @Test
    void nativeAssessmentRecommendationResolvesOnlyTheOwnersCompletedProjectionWithoutWrites() {
        ObjectId ownerId = new ObjectId();
        ObjectId otherId = new ObjectId();
        ObjectId attemptId = new ObjectId();
        String nativeId = "assessment-" + UUID.randomUUID();
        seedAccount(ownerId);
        seedAccount(otherId);
        seedPeriodicTest(attemptId, ownerId);
        mongoTemplate.getCollection("attempts").updateOne(new Document("_id", attemptId),
                new Document("$set", new Document("assessment_source_attempt_id", nativeId)));
        mongoTemplate.getCollection("micro_skill_results").updateOne(
                new Document("attempt_id", attemptId).append("micro_skill", "Reading for Gist"),
                new Document("$set", new Document("correct", 2).append("percent", 20)));
        mongoTemplate.getCollection("question_pool").insertMany(IntStream.range(0, 30).mapToObj(i ->
                new Document("question_id", "pool-" + i).append("micro_skill", "Reading for Gist")
                        .append("level", "A2").append("status", "ACTIVE")).toList());
        Document before = mongoTemplate.getCollection("attempts").find(new Document("_id", attemptId)).first();
        List<Document> resultsBefore = mongoTemplate.getCollection("micro_skill_results").find()
                .into(new java.util.ArrayList<>());
        String base = "http://localhost:" + port + "/api/learning-paths/recommendation";
        String url = base + "/by-assessment?assessmentAttemptId=" + nativeId;
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode resolved = mapper.readTree(authenticatedGet(url, ownerId)).path("data");
        assertThat(resolved).isEqualTo(mapper.readTree(authenticatedGet(
                base + "?attemptId=" + attemptId, ownerId)).path("data"));
        assertThat(resolved.path("state").asString()).isEqualTo("AVAILABLE");
        assertThat(resolved.path("microSkills").size()).isEqualTo(1);
        assertThat(resolved.path("microSkills").get(0).path("name").asString()).isEqualTo("Reading for Gist");
        assertThatThrownBy(() -> authenticatedGet(url + "&userId=" + ownerId, otherId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment?assessmentAttemptId=missing", ownerId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment?assessmentAttemptId=" + attemptId, ownerId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment", ownerId))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        for (String invalid : List.of("", "x".repeat(101))) {
            assertThatThrownBy(() -> authenticatedGet(base + "/by-assessment?assessmentAttemptId=" + invalid, ownerId))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
        assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(mongoTemplate.getCollection("attempts").find(new Document("_id", attemptId)).first()).isEqualTo(before);
        assertThat(mongoTemplate.getCollection("micro_skill_results").find().into(new java.util.ArrayList<>()))
                .containsExactlyElementsOf(resultsBefore);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
    }

    @Test
    void nativeAssessmentRecommendationRejectsIncompleteOrNonPeriodicProjections() {
        ObjectId ownerId = new ObjectId();
        ObjectId attemptId = new ObjectId();
        seedAccount(ownerId);
        seedPeriodicTest(attemptId, ownerId);
        String url = "http://localhost:" + port
                + "/api/learning-paths/recommendation/by-assessment?assessmentAttemptId=native-id";
        mongoTemplate.getCollection("attempts").updateOne(new Document("_id", attemptId),
                new Document("$set", new Document("assessment_source_attempt_id", "native-id").append("status", "IN_PROGRESS")));
        assertThatThrownBy(() -> authenticatedGet(url, ownerId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        mongoTemplate.getCollection("attempts").updateOne(new Document("_id", attemptId),
                new Document("$set", new Document("status", "COMPLETED").append("type", "PLACEMENT")));
        assertThatThrownBy(() -> authenticatedGet(url, ownerId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
    }

    @Test
    void nativeAssessmentCreationReturnsTheOwnedPathAndOpensWeekOneWithoutDuplicatingIt() {
        ObjectId ownerId = new ObjectId();
        ObjectId otherId = new ObjectId();
        seedAccount(ownerId);
        seedAccount(otherId);
        ObjectId attemptId = seedEligibleProjection(ownerId, "native-create-id");
        Document sourceBefore = mongoTemplate.getCollection("attempts").find(new Document("_id", attemptId)).first();
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
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isEqualTo(30);
        assertThat(mongoTemplate.getCollection("attempts").find(new Document("_id", attemptId)).first()).isEqualTo(sourceBefore);
    }

    @Test
    void nativeAssessmentCreationRejectsStaleConfirmationWithoutWritingAPath() {
        ObjectId ownerId = new ObjectId();
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
    void nativeAssessmentCreationValidatesTheBodyAndRequiresACompletedProjection() {
        ObjectId ownerId = new ObjectId();
        seedAccount(ownerId);
        ObjectId attemptId = seedEligibleProjection(ownerId, "native-validation-id");
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
        for (Document changed : List.of(new Document("status", "IN_PROGRESS"),
                new Document("status", "COMPLETED").append("type", "PLACEMENT"))) {
            mongoTemplate.getCollection("attempts").updateOne(new Document("_id", attemptId), new Document("$set", changed));
            assertThatThrownBy(() -> authenticatedRequest(url, HttpMethod.POST, valid, ownerId))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }
        assertThat(mongoTemplate.getCollection("learning_paths").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("learning_path_questions").countDocuments()).isZero();
    }

    @Test
    void listAndSearchExposeOnlyOwnedCompletedAssessmentMappingsAndPreserveSourceIds() {
        ObjectId ownerId = new ObjectId();
        ObjectId otherId = new ObjectId();
        seedAccount(ownerId);
        seedAccount(otherId);
        ObjectId linkedId = new ObjectId();
        ObjectId unmappedId = new ObjectId();
        ObjectId foreignId = new ObjectId();
        ObjectId blankId = new ObjectId();
        ObjectId placementId = new ObjectId();
        ObjectId unfinishedId = new ObjectId();
        ObjectId missingId = new ObjectId();
        ObjectId malformedId = new ObjectId();
        mongoTemplate.getCollection("attempts").insertMany(List.of(
                assessmentProjection(linkedId, ownerId, "native-linked-id"),
                assessmentProjection(unmappedId, ownerId, null),
                assessmentProjection(foreignId, otherId, "foreign-native-id"),
                assessmentProjection(blankId, ownerId, " "),
                assessmentProjection(placementId, ownerId, "placement-native-id").append("type", "PLACEMENT"),
                assessmentProjection(unfinishedId, ownerId, "unfinished-native-id").append("status", "IN_PROGRESS"),
                assessmentProjection(malformedId, ownerId, "unused").append("assessment_source_attempt_id", 123)));
        List<ObjectId> sourceIds = List.of(linkedId, unmappedId, foreignId, blankId, placementId, unfinishedId, missingId, malformedId);
        List<Document> pathDocuments = new ArrayList<>();
        for (int index = 0; index <= sourceIds.size(); index++) {
            Document path = new Document("_id", new ObjectId()).append("user_id", ownerId)
                    .append("status", "IN_PROGRESS").append("is_active", true)
                    .append("created_date", "2026-11-01")
                    .append("created_at", Date.from(Instant.parse("2026-11-01T03:00:00Z").plusSeconds(index)))
                    .append("micro_skills", List.of(new Document("micro_skill", "Reading for Gist")));
            if (index < sourceIds.size()) path.append("source", new Document("attempt_id", sourceIds.get(index)));
            pathDocuments.add(path);
        }
        mongoTemplate.getCollection("learning_paths").insertMany(pathDocuments);
        List<Document> sourcesBefore = mongoTemplate.getCollection("attempts").find().into(new ArrayList<>());
        String base = "http://localhost:" + port + "/api/learning-paths";
        JsonMapper mapper = JsonMapper.builder().build();
        for (String suffix : List.of("?status=ACTIVE", "/search?keyword=01/11/2026", "/search?keyword=Gist")) {
            JsonNode items = mapper.readTree(authenticatedGet(base + suffix, ownerId)).path("data").path("items");
            assertThat(items.size()).as("Items for %s", suffix).isEqualTo(pathDocuments.size());
            int linkedCount = 0;
            for (JsonNode item : items) {
                assertThat(item.has("sourceAttemptId")).isFalse();
                if (item.path("id").asString().equals(pathDocuments.getFirst().getObjectId("_id").toHexString())) {
                    assertThat(item.path("sourceAssessmentAttemptId").asString()).isEqualTo("native-linked-id");
                    linkedCount++;
                } else {
                    assertThat(item.path("sourceAssessmentAttemptId").isNull()).isTrue();
                }
            }
            assertThat(linkedCount).isEqualTo(1);
            JsonNode otherItems = mapper.readTree(authenticatedGet(base + suffix + "&userId=" + ownerId, otherId))
                    .path("data").path("items");
            assertThat(otherItems.size()).isZero();
        }
        assertThat(mongoTemplate.getCollection("learning_paths").find().into(new ArrayList<>()))
                .containsExactlyElementsOf(pathDocuments);
        assertThat(mongoTemplate.getCollection("attempts").find().into(new ArrayList<>()))
                .containsExactlyElementsOf(sourcesBefore);
    }

    private Document assessmentProjection(ObjectId attemptId, ObjectId userId, String nativeId) {
        Document document = new Document("_id", attemptId).append("user_id", userId)
                .append("type", "PERIODIC").append("status", "COMPLETED");
        if (nativeId != null) document.append("assessment_source_attempt_id", nativeId);
        return document;
    }

    private ObjectId seedEligibleProjection(ObjectId userId, String nativeId) {
        ObjectId attemptId = new ObjectId();
        seedPeriodicTest(attemptId, userId);
        mongoTemplate.getCollection("attempts").updateOne(new Document("_id", attemptId),
                new Document("$set", new Document("assessment_source_attempt_id", nativeId)));
        mongoTemplate.getCollection("micro_skill_results").updateOne(
                new Document("attempt_id", attemptId).append("micro_skill", "Reading for Gist"),
                new Document("$set", new Document("correct", 2).append("percent", 20)));
        List<Document> options = List.of(new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"),
                new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        mongoTemplate.getCollection("question_pool").insertMany(IntStream.range(0, 30).mapToObj(i ->
                new Document("question_id", "native-pool-" + i).append("micro_skill", "Reading for Gist")
                        .append("skill", "READING").append("level", "A2").append("status", "ACTIVE")
                        .append("prompt", "Question " + i).append("options", options)
                        .append("correct_option_id", "A").append("explanation", "Explanation")).toList());
        return attemptId;
    }

    private void seedPracticeDay(ObjectId pathId, ObjectId userId) {
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

    private void seedPeriodicTest(ObjectId attemptId, ObjectId userId) {
        mongoTemplate.getCollection("attempts").insertOne(new Document("_id", attemptId)
                .append("user_id", userId).append("type", "PERIODIC").append("status", "COMPLETED")
                .append("result", new Document("level", "A2")));
        List<String> skills = List.of("Reading for Gist", "Reading for Specific Information",
                "Reading for Detail & Relationship", "Vocabulary in Context", "Inference & Author's View");
        mongoTemplate.getCollection("micro_skill_results").insertMany(skills.stream().map(skill ->
                new Document("attempt_id", attemptId).append("user_id", userId)
                        .append("micro_skill", skill).append("skill", "READING")
                        .append("correct", 8).append("total", 10).append("percent", 80)
                        .append("band", "A2").append("is_valid", true)).toList());
    }

    private void seedAccount(ObjectId userId) {
        seedAccount(userId, UserRole.USER);
    }

    private void seedAccount(ObjectId userId, UserRole role) {
        mongoTemplate.getCollection("users").insertOne(new Document("_id", userId)
                .append("email", userId.toHexString() + "@example.com")
                .append("password_hash", "unused")
                .append("role", role.name())
                .append("locked", false));
    }

    private String authenticatedGet(String url, ObjectId userId) {
        return authenticatedRequest(url, HttpMethod.GET, null, userId);
    }

    private String authenticatedRequest(String url, HttpMethod method, String body, ObjectId userId) {
        String sessionId = UUID.randomUUID().toString();
        mongoTemplate.getCollection("refresh_sessions").insertOne(new Document("session_id", sessionId)
                .append("account_id", userId.toHexString())
                .append("expires_at", Date.from(Instant.now().plusSeconds(3600)))
                .append("revoked", false));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN="
                + tokens.create(userId.toHexString(), sessionId, "access", 900));
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody();
    }
}
