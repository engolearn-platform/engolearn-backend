package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.CompletionResultVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemAttemptRepository;
import fit.iuh.engolearn.topic.support.TopicLearningFixture.PublishedItem;
import fit.iuh.engolearn.topic.support.TopicTestAuthentication;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedItemVersion;
import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedMajorItemVersionUpdate;
import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedPublishedItem;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the learner attempt and Topic progress endpoints against a real MongoDB replica set. */
@EnabledIfDockerAvailable
class TopicLearningControllerIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicVersionRepository topicVersionRepository;

    @Autowired
    private TopicItemVersionRepository topicItemVersionRepository;

    @Autowired
    private UserTopicItemAttemptRepository attempts;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String accessToken;

    @BeforeEach
    void signInLearner() {
        accessToken = TopicTestAuthentication.persistedAccessToken(
                userRepository, userProfileRepository, refreshSessionRepository, jwtTokenService);
    }

    @Test
    void getContentReturnsFullItemContentFromRealMongo() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        TopicItemVersion itemVersion = published.itemVersion();
        String attemptId = startAttempt(published.item().getId()).get("attemptId").asString();

        ResponseEntity<String> response = authenticatedGet(attemptUrl(attemptId) + "/content");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Asking for directions at the airport")
                .contains("sân bay")
                .contains("airport_en.mp3")
                .contains("airport_us.mp3");

        var stored = topicItemVersionRepository.findById(itemVersion.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Asking for directions at the airport");
        assertThat(stored.getContext().getScenarioDescription())
                .isEqualTo("You are at the airport asking for directions");
        assertThat(stored.getVocabularies()).hasSize(1);
        assertThat(stored.getVocabularies().get(0).getSnapshot().getAudioEnUrl())
                .isEqualTo("airport_en.mp3");

        JsonNode data = JsonMapper.builder().build().readTree(response.getBody()).get("data");
        JsonNode expressions = data.get("expressions");
        assertThat(expressions.size()).isEqualTo(1);
        assertThat(expressions.get(0).get("tips").asString())
                .isEqualTo("Use the pattern with a place word from the vocabulary step");
        assertThat(expressions.get(0).get("purposes").size()).isEqualTo(1);
        JsonNode purpose = expressions.get(0).get("purposes").get(0);
        assertThat(purpose.get("purposeEn").asString())
                .isEqualTo("Asking for directions politely");
        assertThat(purpose.get("purposeVi").asString())
                .isEqualTo("Hỏi đường một cách lịch sự");
        JsonNode examples = purpose.get("examples");
        assertThat(examples.size()).isEqualTo(2);
        assertThat(examples.get(0).get("textEn").asString())
                .isEqualTo("Could you tell me where the check-in counter is?");
        assertThat(examples.get(0).get("textVi").asString())
                .isEqualTo("Bạn có thể cho tôi biết quầy làm thủ tục ở đâu không?");
        assertThat(examples.get(0).get("usageNote").asString())
                .isEqualTo("Use 'Could you' for a polite request");
    }

    @Test
    void advancePersistsFrontierAndRevisionOnTheOwnedAttempt() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        JsonNode started = startAttempt(published.item().getId());
        assertThat(started.get("position").get("stage").asString()).isEqualTo("CONTEXT");

        JsonNode advanced = advance(started);

        assertThat(advanced.get("topicItemVersionId").asString()).isEqualTo(published.itemVersion().getId());
        assertThat(advanced.get("position").get("stage").asString()).isEqualTo("VOCAB");
        assertThat(advanced.get("position").get("itemIndex").asInt()).isZero();
        assertThat(advanced.get("revision").asLong()).isEqualTo(started.get("revision").asLong() + 1);
        String attemptId = started.get("attemptId").asString();
        assertThat(data(authenticatedGet(attemptUrl(attemptId)))).isEqualTo(advanced);
        var stored = attempts.findById(attemptId).orElseThrow();
        assertThat(stored.getVersion()).isEqualTo(advanced.get("revision").asLong());
        assertThat(stored.getFurthestReached().getStage().name()).isEqualTo("VOCAB");
        assertThat(stored.getMutationReceipts()).hasSize(1);
        assertThat(documentCount("user_topic_item_attempts")).isEqualTo(1);
        assertThat(documentCount("user_topic_attempts")).isZero();
        assertThat(documentCount("user_topic_item_progress")).isZero();
        assertThat(documentCount("user_topic_progress")).isZero();
    }

    @Test
    void majorUpdatePublishedAfterItemStartKeepsContentAndPositionOnPinnedVersion() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        TopicItemVersion pinned = published.itemVersion();
        JsonNode started = startAttempt(published.item().getId());
        assertThat(started.get("topicItemVersionId").asString()).isEqualTo(pinned.getId());
        String url = attemptUrl(started.get("attemptId").asString());

        TopicItemVersion major = seedMajorItemVersionUpdate(published,
                topicRepository, topicVersionRepository, topicItemVersionRepository,
                "Asking for directions at the train station");
        assertThat(major.getId()).isNotEqualTo(pinned.getId());
        assertThat(major.getTitle()).isNotEqualTo(pinned.getTitle());

        Topic topicAfterUpdate = topicRepository.findById(published.topic().getId()).orElseThrow();
        assertThat(topicAfterUpdate.getLatestPublishedVersion()).isEqualTo(major.getTopicVersionId());
        TopicVersion majorRelease = topicVersionRepository
                .findById(major.getTopicVersionId()).orElseThrow();
        assertThat(majorRelease.getItemVersionIds())
                .containsExactly(major.getId())
                .doesNotContain(pinned.getId());

        ResponseEntity<String> contentResponse = authenticatedGet(url + "/content");

        assertThat(contentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JsonMapper.builder().build().readTree(contentResponse.getBody()).get("data");
        assertThat(data.get("topicItemVersionId").asString()).isEqualTo(pinned.getId());
        assertThat(data.get("version").asInt()).isEqualTo(pinned.getVersion());
        assertThat(contentResponse.getBody())
                .contains("Asking for directions at the airport")
                .doesNotContain("Asking for directions at the train station");

        JsonNode advanced = advance(started);
        assertThat(advanced.get("topicItemVersionId").asString()).isEqualTo(pinned.getId());
        assertThat(advanced.get("position").get("stage").asString()).isEqualTo("VOCAB");
        assertThat(advanced.get("position").get("itemIndex").asInt()).isZero();
    }

    @Test
    void getTopicProgressReportsCountsAndPerItemStatusFromRealMongo() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        TopicItemVersion completedVersion = published.itemVersion();
        TopicItemVersion inProgressVersion = seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Ordering at a cafe", 2, ContentStatus.PUBLISHED);
        TopicItemVersion notStartedVersion = seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Checking in at the hotel", 3, ContentStatus.PUBLISHED);
        TopicItemVersion hiddenVersion = seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Hidden draft", 4, ContentStatus.HIDDEN);

        Instant completedAt = Instant.parse("2020-01-01T00:00:00Z");
        saveCompletedAttempt(completedVersion, completedAt, 3, 4);
        startAttempt(inProgressVersion.getTopicItemId());

        ResponseEntity<String> response = authenticatedGet(
                "http://localhost:" + port + "/api/v1/topics/"
                        + published.topic().getId() + "/progress");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JsonMapper.builder().build().readTree(response.getBody());
        assertThat(body.get("timestamp").isNull()).isFalse();
        JsonNode data = body.get("data");
        assertThat(data.get("topicId").asString()).isEqualTo(published.topic().getId());
        assertThat(data.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(data.get("completedItemCount").asInt()).isEqualTo(1);
        assertThat(data.get("totalItemCount").asInt()).isEqualTo(3);
        assertThat(Instant.parse(data.get("lastAccessedAt").asString())).isAfter(completedAt);

        JsonNode items = data.get("items");
        assertThat(items.size()).isEqualTo(3);

        JsonNode completed = items.get(0);
        assertThat(completed.get("topicItemId").asString()).isEqualTo(completedVersion.getTopicItemId());
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.get("firstTryCorrectCount").asInt()).isEqualTo(3);
        assertThat(completed.get("totalQuizCount").asInt()).isEqualTo(4);
        assertThat(Instant.parse(completed.get("completedAt").asString())).isEqualTo(completedAt);

        JsonNode inProgress = items.get(1);
        assertThat(inProgress.get("topicItemId").asString()).isEqualTo(inProgressVersion.getTopicItemId());
        assertThat(inProgress.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(inProgress.get("completedAt").isNull()).isTrue();

        JsonNode notStarted = items.get(2);
        assertThat(notStarted.get("topicItemId").asString()).isEqualTo(notStartedVersion.getTopicItemId());
        assertThat(notStarted.get("status").asString()).isEqualTo("NOT_STARTED");
        assertThat(notStarted.get("firstTryCorrectCount").isNull()).isTrue();
        assertThat(notStarted.get("totalQuizCount").isNull()).isTrue();

        assertThat(response.getBody()).doesNotContain(hiddenVersion.getTopicItemId());
    }

    @Test
    void getTopicProgressWithOnlyHiddenItemsInManifestReturnsEmptyNotStartedProgress() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        TopicItemVersion hiddenVersion = seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Hidden draft", 2, ContentStatus.HIDDEN);
        published.release().setItemVersionIds(List.of(hiddenVersion.getId()));
        topicVersionRepository.save(published.release());

        TopicVersion storedRelease = topicVersionRepository
                .findById(published.release().getId()).orElseThrow();
        TopicItemVersion storedHidden = topicItemVersionRepository
                .findById(hiddenVersion.getId()).orElseThrow();
        assertThat(storedRelease.getItemVersionIds()).containsExactly(storedHidden.getId());
        assertThat(storedHidden.getStatus()).isEqualTo(ContentStatus.HIDDEN);

        ResponseEntity<String> response = authenticatedGet(
                "http://localhost:" + port + "/api/v1/topics/"
                        + published.topic().getId() + "/progress");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JsonMapper.builder().build().readTree(response.getBody()).get("data");
        assertThat(data.get("topicId").asString()).isEqualTo(published.topic().getId());
        assertThat(data.get("status").asString()).isEqualTo("NOT_STARTED");
        assertThat(data.get("completedItemCount").asInt()).isEqualTo(0);
        assertThat(data.get("totalItemCount").asInt()).isEqualTo(0);
        assertThat(data.get("lastAccessedAt").isNull()).isTrue();
        assertThat(data.get("items").size()).isEqualTo(0);
    }

    @Test
    void getTopicProgressWithoutAnyRecordsReturnsNotStartedWithNullLastAccessedAt() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Ordering at a cafe", 2, ContentStatus.PUBLISHED);
        seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Checking in at the hotel", 3, ContentStatus.PUBLISHED);

        ResponseEntity<String> response = authenticatedGet(
                "http://localhost:" + port + "/api/v1/topics/" + published.topic().getId() + "/progress");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JsonMapper.builder().build().readTree(response.getBody()).get("data");
        assertThat(data.get("status").asString()).isEqualTo("NOT_STARTED");
        assertThat(data.get("completedItemCount").asInt()).isEqualTo(0);
        assertThat(data.get("totalItemCount").asInt()).isEqualTo(3);
        assertThat(data.get("lastAccessedAt").isNull()).isTrue();

        JsonNode items = data.get("items");
        assertThat(items.size()).isEqualTo(3);
        for (JsonNode item : items) {
            assertThat(item.get("status").asString()).isEqualTo("NOT_STARTED");
            assertThat(item.get("firstTryCorrectCount").isNull()).isTrue();
            assertThat(item.get("totalQuizCount").isNull()).isTrue();
            assertThat(item.get("completedAt").isNull()).isTrue();
        }

        assertThat(documentCount("user_topic_progress")).isZero();
        assertThat(documentCount("user_topic_attempts")).isZero();
    }

    @Test
    void getTopicProgressWhenEveryVisibleItemIsCompletedReturnsCompleted() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        TopicItemVersion secondVersion = seedItemVersion(published,
                topicItemRepository, topicVersionRepository, topicItemVersionRepository,
                "Ordering at a cafe", 2, ContentStatus.PUBLISHED);
        TopicItemVersion firstVersion = published.itemVersion();

        Instant completedAt = Instant.parse("2020-01-01T00:00:00Z");
        for (TopicItemVersion version : List.of(firstVersion, secondVersion)) {
            saveCompletedAttempt(version, completedAt, 2, 2);
        }

        ResponseEntity<String> response = authenticatedGet(
                "http://localhost:" + port + "/api/v1/topics/"
                        + published.topic().getId() + "/progress");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = JsonMapper.builder().build().readTree(response.getBody()).get("data");
        assertThat(data.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(data.get("completedItemCount").asInt()).isEqualTo(2);
        assertThat(data.get("totalItemCount").asInt()).isEqualTo(2);

        JsonNode items = data.get("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("topicItemId").asString())
                .isEqualTo(firstVersion.getTopicItemId());
        assertThat(items.get(0).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(items.get(1).get("topicItemId").asString())
                .isEqualTo(secondVersion.getTopicItemId());
        assertThat(items.get(1).get("status").asString()).isEqualTo("COMPLETED");
    }

    private long documentCount(String collection) {
        return mongoTemplate.getCollection(collection).countDocuments();
    }

    private ResponseEntity<String> authenticatedGet(String url) {
        return restTemplate.exchange(url, HttpMethod.GET, authenticatedEntity(), String.class);
    }

    private JsonNode startAttempt(String itemId) {
        return post("http://localhost:" + port + "/api/v1/topic-items/" + itemId + "/attempts",
                "{\"mode\":\"LEARN\"}");
    }

    private JsonNode advance(JsonNode attempt) {
        JsonNode position = attempt.get("position");
        String body = """
                {"revision":%d,"stage":"%s","itemIndex":%d}
                """.formatted(attempt.get("revision").asLong(), position.get("stage").asString(),
                position.get("itemIndex").asInt());
        return post(attemptUrl(attempt.get("attemptId").asString()) + "/advance", body);
    }

    private JsonNode post(String url, String body) {
        HttpHeaders headers = authenticatedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return data(restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class));
    }

    private JsonNode data(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonMapper.builder().build().readTree(response.getBody()).get("data");
    }

    private String attemptUrl(String attemptId) {
        return "http://localhost:" + port + "/api/v1/topic-attempts/" + attemptId;
    }

    private void saveCompletedAttempt(TopicItemVersion version, Instant completedAt, int correct, int total) {
        attempts.insert(UserTopicItemAttempt.builder()
                .userId(TopicTestAuthentication.USER_ID)
                .topicId(version.getTopicId())
                .topicItemId(version.getTopicItemId())
                .topicItemVersionId(version.getId())
                .attemptType(AttemptType.LEARNING)
                .status(AttemptStatus.COMPLETED)
                .startedAt(completedAt.minusSeconds(60))
                .completedAt(completedAt)
                .lastAccessedAt(completedAt)
                .completionResult(CompletionResultVo.builder()
                        .title(version.getTitle())
                        .firstTryCorrectCount(correct)
                        .totalQuizCount(total)
                        .build())
                .build());
    }

    private HttpEntity<Void> authenticatedEntity() {
        return new HttpEntity<>(authenticatedHeaders());
    }

    private HttpHeaders authenticatedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
