package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;
import fit.iuh.engolearn.speaking.repository.SpeakingItemConfigRepository;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("dev")
@Order(1)
public class SpeakingDevFixture implements ApplicationRunner {
    public static final String ITEM_ID = "690000000000000000000002";
    private static final String TOPIC_ID = "690000000000000000000001";
    private static final String RELEASE_ID = "690000000000000000000003";
    private static final String ITEM_VERSION_ID = "690000000000000000000004";

    private final TopicRepository topics;
    private final TopicVersionRepository releases;
    private final TopicItemRepository items;
    private final TopicItemVersionRepository versions;
    private final SpeakingItemConfigRepository configs;

    public SpeakingDevFixture(TopicRepository topics, TopicVersionRepository releases,
            TopicItemRepository items, TopicItemVersionRepository versions,
            SpeakingItemConfigRepository configs) {
        this.topics = topics;
        this.releases = releases;
        this.items = items;
        this.versions = versions;
        this.configs = configs;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        if (!releases.existsById(RELEASE_ID)) {
            releases.save(TopicVersion.builder().id(RELEASE_ID).topicId(TOPIC_ID).version(1)
                    .status(ContentStatus.PUBLISHED).publishedAt(now)
                    .itemVersionIds(List.of(ITEM_VERSION_ID)).build());
        }
        if (!items.existsById(ITEM_ID)) {
            items.save(TopicItem.builder().id(ITEM_ID).topicId(TOPIC_ID).build());
        }
        if (!versions.existsById(ITEM_VERSION_ID)) {
            versions.save(TopicItemVersion.builder().id(ITEM_VERSION_ID)
                    .topicItemId(ITEM_ID).topicId(TOPIC_ID).topicVersionId(RELEASE_ID)
                    .version(1).changeType(ChangeType.MAJOR).status(ContentStatus.PUBLISHED)
                    .title("Asking for directions at the airport")
                    .description("You are at an airport. Ask an information desk agent how to reach your gate, confirm the boarding time, and clarify one direction you did not understand.")
                    .order(1).durationMinutes(10).publishedAt(now)
                    .vocabularies(List.of()).expressions(List.of()).build());
        }
        if (!topics.existsById(TOPIC_ID)) {
            topics.save(Topic.builder().id(TOPIC_ID).titleEn("Speaking AI test fixture")
                    .titleVi("Dữ liệu mẫu luyện nói AI")
                    .description("Development-only topic for a direct Speaking session test.")
                    .cefrLevel(CefrLevel.A1).category(TopicCategory.TRAVEL)
                    .orderIndex(999).durationMinutes(10)
                    .latestPublishedVersion(RELEASE_ID).build());
        }
        if (configs.findByTopicItemVersionId(ITEM_VERSION_ID).isEmpty()) {
            configs.save(SpeakingItemConfig.builder().topicItemId(ITEM_ID)
                    .topicItemVersionId(ITEM_VERSION_ID)
                    .goals(List.of(
                            new SpeakingGoal("ask-gate", "Ask where the gate is",
                                    "Ask the agent how to get to your departure gate.", SpeakingGoal.Priority.REQUIRED),
                            new SpeakingGoal("confirm-time", "Confirm boarding time",
                                    "Ask when boarding begins.", SpeakingGoal.Priority.REQUIRED),
                            new SpeakingGoal("clarify-direction", "Clarify a direction",
                                    "Ask the agent to repeat or explain one direction.", SpeakingGoal.Priority.OPTIONAL)))
                    .minimumValidTurns(3).revision(1)
                    .status(SpeakingItemConfig.Status.PUBLISHED).build());
        }
    }
}
