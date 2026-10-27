package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.support.TopicLearningFixture.PublishedItem;
import fit.iuh.engolearn.topic.utils.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedPublishedItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * Pins the unauthenticated contract of the four learning endpoints: every one of them requires
 * a user id before touching the database, so all answer 422 and no progress document is written.
 */
@EnabledIfDockerAvailable
class TopicLearningUnauthenticatedIntegrationTest extends AbstractMongoIntegrationTest {

    private static final String SIGN_IN_DETAIL = "Sign in to learn a topic item";

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicVersionRepository topicVersionRepository;

    @Autowired
    private TopicItemVersionRepository topicItemVersionRepository;

    @Test
    void everyLearningEndpointRejectsANullUserIdWith422AndWritesNoProgress() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        String itemUrl = "http://localhost:" + port + "/api/v1/topic-items/" + published.item().getId();
        String topicUrl = "http://localhost:" + port + "/api/v1/topics/" + published.topic().getId();

        assertSignInRequired422(itemUrl + "/content", HttpMethod.GET, null);
        assertSignInRequired422(itemUrl + "/position", HttpMethod.GET, null);
        assertSignInRequired422(itemUrl + "/position", HttpMethod.PUT, jsonEntity());
        assertSignInRequired422(topicUrl + "/progress", HttpMethod.GET, null);

        assertNoProgressDocuments();
    }

    @Test
    void blankUserIdIsTreatedAsUnauthenticated() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);
        when(currentUserProvider.getCurrentUserId()).thenReturn("   ");

        String positionUrl = "http://localhost:" + port + "/api/v1/topic-items/"
                + published.item().getId() + "/position";

        assertSignInRequired422(positionUrl, HttpMethod.PUT, jsonEntity());

        assertNoProgressDocuments();
    }

    private static HttpEntity<String> jsonEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>("{\"stage\":\"VOCAB\",\"itemIndex\":0}", headers);
    }

    private void assertSignInRequired422(String url, HttpMethod method, HttpEntity<String> entity) {
        RestClientResponseException exception = catchThrowableOfType(
                () -> restTemplate.exchange(url, method, entity, String.class),
                RestClientResponseException.class);
        assertThat(exception.getStatusCode().value()).isEqualTo(422);
        assertThat(exception.getResponseBodyAsString())
                .contains("\"title\":\"Learning precondition failed\"")
                .contains("\"detail\":\"" + SIGN_IN_DETAIL + "\"");
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
