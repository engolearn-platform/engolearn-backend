package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemProgressRepository;
import fit.iuh.engolearn.topic.support.TopicLearningFixture.PublishedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static fit.iuh.engolearn.topic.support.TopicLearningFixture.seedPublishedItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Proves the position write is one transaction: a failure on the third of the four writes
 * leaves none of the progress collections with a document.
 */
@EnabledIfDockerAvailable
class TopicLearningRollbackIntegrationTest extends AbstractMongoIntegrationTest {

    @MockitoSpyBean
    private UserTopicItemProgressRepository userTopicItemProgressRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicVersionRepository topicVersionRepository;

    @Autowired
    private TopicItemVersionRepository topicItemVersionRepository;

    @Test
    void failureWhileSavingItemProgressRollsBackTheWholePositionWrite() {
        PublishedItem published = seedPublishedItem(
                topicRepository, topicItemRepository, topicVersionRepository, topicItemVersionRepository);

        // No doCallRealMethod: the spy is a JDK proxy of the interface, so real-method chaining
        // is impossible; the verify below pins the single save call this flow makes.
        doThrow(new RuntimeException("injected item-progress write failure"))
                .when(userTopicItemProgressRepository).save(any(UserTopicItemProgress.class));

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String positionUrl = "http://localhost:" + port + "/api/v1/topic-items/"
                + published.item().getId() + "/position";

        RestClientResponseException exception = catchThrowableOfType(
                () -> restTemplate.exchange(positionUrl, HttpMethod.PUT,
                        new HttpEntity<>("{\"stage\":\"VOCAB\",\"itemIndex\":0}", jsonHeaders),
                        String.class),
                RestClientResponseException.class);

        assertThat(exception.getStatusCode().is5xxServerError()).isTrue();
        verify(userTopicItemProgressRepository).save(any(UserTopicItemProgress.class));

        assertThat(documentCount("user_topic_attempts")).isZero();
        assertThat(documentCount("user_topic_item_attempts")).isZero();
        assertThat(documentCount("user_topic_item_progress")).isZero();
        assertThat(documentCount("user_topic_progress")).isZero();
    }

    private long documentCount(String collection) {
        return mongoTemplate.getCollection(collection).countDocuments();
    }
}
