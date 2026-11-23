package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.support.TopicLearningFixture.PublishedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;

import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedPublishedItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Learner endpoints reject anonymous requests before creating attempts or progress.
 */
@EnabledIfDockerAvailable
class TopicLearningUnauthenticatedIntegrationTest extends AbstractMongoIntegrationTest {

    TopicLearningUnauthenticatedIntegrationTest() {
        restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    }

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicVersionRepository topicVersionRepository;

    @Autowired
    private TopicItemVersionRepository topicItemVersionRepository;

    @Test
    void everyLearningEndpointRejectsAGuestWith401AndWritesNoProgress() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);

        String itemUrl = "http://localhost:" + port + "/api/v1/topic-items/" + published.item().getId();
        String topicUrl = "http://localhost:" + port + "/api/v1/topics/" + published.topic().getId();
        String attemptUrl = "http://localhost:" + port + "/api/v1/topic-attempts/" + published.item().getId();

        assertSignInRequired401(itemUrl + "/attempts", HttpMethod.POST, jsonEntity("{\"mode\":\"LEARN\"}"));
        assertSignInRequired401(attemptUrl, HttpMethod.GET, null);
        assertSignInRequired401(attemptUrl + "/content", HttpMethod.GET, null);
        assertSignInRequired401(attemptUrl + "/advance", HttpMethod.POST,
                jsonEntity("{\"revision\":0,\"stage\":\"CONTEXT\",\"itemIndex\":0}"));
        assertSignInRequired401(topicUrl + "/progress", HttpMethod.GET, null);

        assertNoProgressDocuments();
    }

    @Test
    void guestAttemptStartIsRejectedBeforeBodyProcessing() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);

        String startUrl = "http://localhost:" + port + "/api/v1/topic-items/"
                + published.item().getId() + "/attempts";

        assertSignInRequired401(startUrl, HttpMethod.POST, jsonEntity("{"));

        assertNoProgressDocuments();
    }

    private static HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_PROBLEM_JSON));
        headers.set("Idempotency-Key", "guest-request");
        return new HttpEntity<>(body, headers);
    }

    private void assertSignInRequired401(String url, HttpMethod method, HttpEntity<String> entity) {
        HttpEntity<String> request = entity == null ? problemEntity() : entity;
        RestClientResponseException exception = catchThrowableOfType(
                () -> restTemplate.exchange(url, method, request, String.class),
                RestClientResponseException.class);
        assertThat(exception.getStatusCode().value()).isEqualTo(401);
        assertThat(exception.getResponseHeaders().getContentType())
                .matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith);
        assertThat(exception.getResponseBodyAsString())
                .contains("\"status\":401");
    }

    private static HttpEntity<String> problemEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_PROBLEM_JSON));
        return new HttpEntity<>(headers);
    }

    private void assertNoProgressDocuments() {
        assertThat(documentCount("user_topic_attempts")).isZero();
        assertThat(documentCount("user_topic_item_attempts")).isZero();
        assertThat(documentCount("user_topic_item_progress")).isZero();
        assertThat(documentCount("user_topic_progress")).isZero();
    }

    private long documentCount(String collection) {
        return mongoTemplate.getCollection(collection).countDocuments();
    }
}
