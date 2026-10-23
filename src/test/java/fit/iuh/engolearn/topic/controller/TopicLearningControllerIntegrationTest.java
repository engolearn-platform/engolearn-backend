package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Representative API integration test for UC03 (learn topic_item).
 *
 * <p>Runs against a real MongoDB replica set via Testcontainers. Verifies the full
 * HTTP -> Spring Boot -> MongoDB flow for {@code GET /topic-items/{itemId}/content},
 * including that the response shape matches the DTO and data is actually persisted.
 */
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

    @Test
    void getContentReturnsFullItemContentFromRealMongo() {
        Topic topic = topicRepository.save(Topic.builder()
                .titleEn("Travel topic")
                .titleVi("Chủ đề Du lịch")
                .description("Travel around")
                .cefrLevel(CefrLevel.A1)
                .orderIndex(1)
                .durationMinutes(30)
                .coverUrl("https://cdn.example.test/travel.jpg")
                .build());

        TopicItem item = topicItemRepository.save(TopicItem.builder()
                .topicId(topic.getId())
                .build());

        TopicVersion release = topicVersionRepository.save(TopicVersion.builder()
                .topicId(topic.getId())
                .version(1)
                .status(ContentStatus.PUBLISHED)
                .itemVersionIds(new ArrayList<>())
                .publishedAt(Instant.now())
                .build());

        TopicItemVersion itemVersion = topicItemVersionRepository.save(TopicItemVersion.builder()
                .id("iv1")
                .topicItemId(item.getId())
                .topicId(topic.getId())
                .topicVersionId(release.getId())
                .version(1)
                .changeType(ChangeType.MAJOR)
                .title("Asking for directions at the airport")
                .description("How to ask for directions at the airport")
                .order(1)
                .status(ContentStatus.PUBLISHED)
                .durationMinutes(5)
                .context(TopicContextVo.builder()
                        .scenarioDescription("You are at the airport asking for directions")
                        .prompt("Where is the check-in counter?")
                        .build())
                .vocabularies(List.of(ItemWordVo.builder()
                        .vocabId("v1")
                        .senseId("s1")
                        .snapshot(WordSnapshotVo.builder()
                                .word("airport")
                                .phonetic("/ˈeər.pɔːt/")
                                .sense("sân bay")
                                .describeVi("sân bay")
                                .pos("noun")
                                .audioUrl("airport.mp3")
                                .audioEnUrl("airport_en.mp3")
                                .audioUsUrl("airport_us.mp3")
                                .build())
                        .orderIndex(1)
                        .build()))
                .expressions(List.of())
                .quizzes(List.of())
                .build());

        // The release manifest and the topic pointer must both reference the item
        // version id, otherwise the item is not reachable through the published release.
        release.setItemVersionIds(List.of(itemVersion.getId()));
        topicVersionRepository.save(release);

        topic.setLatestPublishedVersion(release.getId());
        topicRepository.save(topic);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/topic-items/" + item.getId() + "/content",
                String.class);

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
    }
}
