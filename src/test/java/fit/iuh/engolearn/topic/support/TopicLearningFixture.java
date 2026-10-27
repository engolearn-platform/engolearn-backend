package fit.iuh.engolearn.topic.support;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeding helpers for topic-learning integration tests: repositories are passed in, and
 * the base class drops {@code engolearn_it} before each test, so each test seeds its own graph.
 */
public final class TopicLearningFixture {

    private TopicLearningFixture() {
    }

    public record PublishedItem(Topic topic, TopicVersion release, TopicItem item,
                                 TopicItemVersion itemVersion) {
    }

    public static PublishedItem seedPublishedItem(TopicRepository topicRepository,
                                                  TopicItemRepository topicItemRepository,
                                                  TopicVersionRepository topicVersionRepository,
                                                  TopicItemVersionRepository topicItemVersionRepository) {
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

        // No explicit id: the item attempt stores this id as ObjectId, so only a generated
        // hex id (the shape DevTopicSeedRunner produces) survives the position write.
        TopicItemVersion itemVersion = topicItemVersionRepository.save(TopicItemVersion.builder()
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
                .expressions(List.of(ExpressionBlockVo.builder()
                        .purposes(List.of(ExpressionPurposeVo.builder()
                                .purposeEn("Asking for directions politely")
                                .purposeVi("Hỏi đường một cách lịch sự")
                                .orderIndex(1)
                                .examples(List.of(
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.POLITE_INQUIRY)
                                                .textEn("Could you tell me where the check-in counter is?")
                                                .textVi("Bạn có thể cho tôi biết quầy làm thủ tục ở đâu không?")
                                                .usageNote("Use 'Could you' for a polite request")
                                                .audioUrl("expr_polite.mp3")
                                                .linkedVocabIds(List.of("v1"))
                                                .build(),
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.BASIC_SUGGESTION)
                                                .textEn("Go straight and turn left at the gate.")
                                                .textVi("Đi thẳng rồi rẽ trái ở cửa ra vào.")
                                                .usageNote("Short directions use the imperative form")
                                                .audioUrl("expr_direct.mp3")
                                                .linkedVocabIds(List.of())
                                                .build()))
                                .build()))
                        .tips("Use the pattern with a place word from the vocabulary step")
                        .build()))
                .quizzes(List.of())
                .build());

        // The release manifest and the topic pointer must both reference the item
        // version id, otherwise the item is not reachable through the published release.
        release.setItemVersionIds(List.of(itemVersion.getId()));
        topicVersionRepository.save(release);

        topic.setLatestPublishedVersion(release.getId());
        topicRepository.save(topic);

        return new PublishedItem(topic, release, item, itemVersion);
    }

    public static TopicItemVersion seedItemVersion(PublishedItem published,
                                                   TopicItemRepository topicItemRepository,
                                                   TopicVersionRepository topicVersionRepository,
                                                   TopicItemVersionRepository topicItemVersionRepository,
                                                   String title, int order, ContentStatus status) {
        TopicItem item = topicItemRepository.save(TopicItem.builder()
                .topicId(published.topic().getId())
                .build());

        TopicItemVersion itemVersion = topicItemVersionRepository.save(TopicItemVersion.builder()
                .topicItemId(item.getId())
                .topicId(published.topic().getId())
                .topicVersionId(published.release().getId())
                .version(1)
                .changeType(ChangeType.MAJOR)
                .title(title)
                .description("Description for " + title)
                .order(order)
                .status(status)
                .durationMinutes(5)
                .context(TopicContextVo.builder()
                        .scenarioDescription("Scenario for " + title)
                        .prompt("Prompt for " + title)
                        .build())
                .vocabularies(List.of())
                .expressions(List.of())
                .quizzes(List.of())
                .build());

        List<String> manifest = new ArrayList<>(published.release().getItemVersionIds());
        manifest.add(itemVersion.getId());
        published.release().setItemVersionIds(manifest);
        topicVersionRepository.save(published.release());

        return itemVersion;
    }

    /**
     * Publishes a MAJOR update of the same item as a new release; the topic pointer must
     * move with it, otherwise the read path still serves the previous release.
     */
    public static TopicItemVersion seedMajorItemVersionUpdate(PublishedItem published,
                                                               TopicRepository topicRepository,
                                                               TopicVersionRepository topicVersionRepository,
                                                               TopicItemVersionRepository topicItemVersionRepository,
                                                               String title) {
        TopicVersion release = topicVersionRepository.save(TopicVersion.builder()
                .topicId(published.topic().getId())
                .version(2)
                .status(ContentStatus.PUBLISHED)
                .itemVersionIds(new ArrayList<>())
                .publishedAt(Instant.now())
                .build());

        TopicItemVersion itemVersion = topicItemVersionRepository.save(TopicItemVersion.builder()
                .topicItemId(published.item().getId())
                .topicId(published.topic().getId())
                .topicVersionId(release.getId())
                .version(2)
                .changeType(ChangeType.MAJOR)
                .title(title)
                .description("Description for " + title)
                .order(1)
                .status(ContentStatus.PUBLISHED)
                .durationMinutes(5)
                .context(TopicContextVo.builder()
                        .scenarioDescription("Scenario for " + title)
                        .prompt("Prompt for " + title)
                        .build())
                .vocabularies(List.of())
                .expressions(List.of())
                .quizzes(List.of())
                .build());

        release.setItemVersionIds(List.of(itemVersion.getId()));
        topicVersionRepository.save(release);

        published.topic().setLatestPublishedVersion(release.getId());
        topicRepository.save(published.topic());

        return itemVersion;
    }
}
