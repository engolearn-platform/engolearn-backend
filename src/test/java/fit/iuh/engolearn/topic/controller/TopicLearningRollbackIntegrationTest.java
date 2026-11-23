package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.support.TopicLearningFixture.PublishedItem;
import fit.iuh.engolearn.topic.support.TopicTestAuthentication;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedPublishedItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * MongoDB must reject the entire attempt update if it cannot persist the new revision.
 */
@EnabledIfDockerAvailable
class TopicLearningRollbackIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicVersionRepository topicVersionRepository;

    @Autowired
    private TopicItemVersionRepository topicItemVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void rejectedAdvanceLeavesTheSavedAttemptAndProgressUnchanged() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        jsonHeaders.set(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + TopicTestAuthentication.persistedAccessToken(
                userRepository, userProfileRepository, refreshSessionRepository, jwtTokenService));
        jsonHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        String api = "http://localhost:" + port + "/api/v1";
        var started = restTemplate.postForEntity(api + "/topic-items/" + published.item().getId() + "/attempts",
                new HttpEntity<>("{\"mode\":\"LEARN\"}", jsonHeaders), String.class);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        var attempt = JsonMapper.builder().build().readTree(started.getBody()).get("data");
        long revision = attempt.get("revision").asLong();
        var storedAttempts = mongoTemplate.getCollection("user_topic_item_attempts");
        Document before = storedAttempts.find().first();
        assertThat(before).isNotNull();

        // Allow the saved attempt but reject any write that increments its revision.
        mongoTemplate.getDb().runCommand(new Document("collMod", storedAttempts.getNamespace().getCollectionName())
                .append("validator", new Document("version", new Document("$lte", revision))));
        jsonHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        String advanceBody = """
                {"revision":%d,"stage":"CONTEXT","itemIndex":0}
                """.formatted(revision);

        RestClientResponseException exception = catchThrowableOfType(
                () -> restTemplate.postForEntity(api + "/topic-attempts/" + attempt.get("attemptId").asString() + "/advance",
                        new HttpEntity<>(advanceBody, jsonHeaders), String.class),
                RestClientResponseException.class);

        assertThat(exception.getStatusCode().is5xxServerError()).isTrue();
        assertThat(storedAttempts.countDocuments()).isEqualTo(1);
        assertThat(storedAttempts.find().first()).isEqualTo(before);

        assertThat(documentCount("user_topic_attempts")).isZero();
        assertThat(documentCount("user_topic_item_progress")).isZero();
        assertThat(documentCount("user_topic_progress")).isZero();
    }

    private long documentCount(String collection) {
        return mongoTemplate.getCollection(collection).countDocuments();
    }
}
