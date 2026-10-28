package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;
import fit.iuh.engolearn.speaking.repository.SpeakingItemConfigRepository;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakingDevFixtureTest {
    @Test
    void seedsPublishedOpenableItemAndConfigWithoutDuplicatingOnRestart() throws Exception {
        Map<String, Topic> topics = new HashMap<>();
        Map<String, TopicVersion> releases = new HashMap<>();
        Map<String, TopicItem> items = new HashMap<>();
        Map<String, TopicItemVersion> versions = new HashMap<>();
        Map<String, SpeakingItemConfig> configs = new HashMap<>();
        SpeakingDevFixture fixture = new SpeakingDevFixture(
                repository(TopicRepository.class, topics),
                repository(TopicVersionRepository.class, releases),
                repository(TopicItemRepository.class, items),
                repository(TopicItemVersionRepository.class, versions),
                repository(SpeakingItemConfigRepository.class, configs));

        fixture.run(null);
        fixture.run(null);

        assertEquals(1, topics.size());
        assertEquals(1, releases.size());
        assertEquals(1, items.size());
        assertEquals(1, versions.size());
        assertEquals(1, configs.size());
        TopicItem item = items.get(SpeakingDevFixture.ITEM_ID);
        Topic topic = topics.get(item.getTopicId());
        TopicVersion release = releases.get(topic.getLatestPublishedVersion());
        TopicItemVersion version = versions.get(release.getItemVersionIds().getFirst());
        SpeakingItemConfig config = configs.values().iterator().next();
        assertEquals(ContentStatus.PUBLISHED, release.getStatus());
        assertEquals(ContentStatus.PUBLISHED, version.getStatus());
        assertEquals(item.getId(), version.getTopicItemId());
        assertEquals(version.getId(), config.getTopicItemVersionId());
        assertEquals(SpeakingItemConfig.Status.PUBLISHED, config.getStatus());
        assertEquals(2, config.requiredGoalCount());
        assertTrue(config.getGoals().stream().allMatch(goal -> goal.id() != null));
    }

    @SuppressWarnings("unchecked")
    private static <T extends BaseDocument, R> R repository(Class<R> type, Map<String, T> documents) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsById" -> documents.containsKey(args[0]);
                    case "save" -> {
                        T document = (T) args[0];
                        documents.put(document.getId(), document);
                        yield document;
                    }
                    case "findByTopicItemVersionId" -> documents.values().stream()
                            .map(document -> (SpeakingItemConfig) document)
                            .filter(config -> config.getTopicItemVersionId().equals(args[0]))
                            .findFirst();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
