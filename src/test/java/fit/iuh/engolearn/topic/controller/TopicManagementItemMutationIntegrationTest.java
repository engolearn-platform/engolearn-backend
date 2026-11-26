package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemAttemptRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemProgressRepository;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicManagementItemMutationIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private TopicItemVersionRepository versions;
    @Autowired private TopicVersionRepository releases;
    @Autowired private UserTopicItemProgressRepository progress;
    @Autowired private UserTopicItemAttemptRepository attempts;

    @Test
    void createsAndAppendsDraftItemsThenUpdatesAndReordersOnlyUnpublishedTopic() throws Exception {
        Topic topic = topic();
        String firstId = create(topic, 0, "First", 1);
        String secondId = create(topic, 1, "Second", 2);
        mvc.perform(patch(BASE + "/{id}/items/{itemId}", topic.getId(), firstId)
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":2,\"title\":\"First edited\",\"description\":null,\"durationMinutes\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("First edited"))
                .andExpect(jsonPath("$.data.items[0].description").isEmpty())
                .andExpect(jsonPath("$.data.items[0].durationMinutes").isEmpty());

        mvc.perform(patch(BASE + "/{id}/items/order", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":3,\"itemIds\":[\"" + secondId + "\",\"" + firstId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(secondId));
        assertThat(versions.findManagementMetadataForItem(topic.getId(), firstId)).singleElement()
                .satisfies(version -> assertThat(version.getTitle()).isEqualTo("First edited"));
    }

    @Test
    void hidesAndUnhidesPublishedItemVersionInPlaceAndPreservesRelease() throws Exception {
        Topic topic = topic();
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion version = version(topic, item, 1, 1, ContentStatus.PUBLISHED);
        List<String> manifest = new ArrayList<>(List.of(version.getId()));
        for (int index = 2; index <= 6; index++) {
            TopicItem additional = items.save(TopicItem.builder().topicId(topic.getId()).build());
            manifest.add(version(topic, additional, 1, index, ContentStatus.PUBLISHED).getId());
        }
        TopicVersion release = releases.save(TopicVersion.builder().topicId(topic.getId()).version(1)
                .status(ContentStatus.PUBLISHED).itemVersionIds(manifest).build());
        topic.setLatestPublishedVersion(release.getId());
        topic = topics.save(topic);

        mvc.perform(patch(BASE + "/{id}/items/{itemId}/visibility", topic.getId(), item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"hidden\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("HIDDEN"));
        assertThat(versions.findById(version.getId()).orElseThrow().getId()).isEqualTo(version.getId());
        assertThat(releases.findById(release.getId()).orElseThrow().getItemVersionIds()).containsExactlyElementsOf(manifest);

        mvc.perform(patch(BASE + "/{id}/items/{itemId}/visibility", topic.getId(), item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"hidden\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"));
    }

    @Test
    void deletesOnlyAnUnreferencedSingleDraftAndRejectsItemWithLearnerProgress() throws Exception {
        Topic topic = topic();
        String deletableId = create(topic, 0, "Temporary", 1);
        mvc.perform(delete(BASE + "/{id}/items/{itemId}", topic.getId(), deletableId)
                        .param("revision", "1").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(items.findById(deletableId)).isEmpty();
        assertThat(versions.findManagementMetadataForItem(topic.getId(), deletableId)).isEmpty();

        String protectedId = create(topic, 2, "Has progress", 1);
        progress.save(UserTopicItemProgress.builder().userId("learner").topicId(topic.getId())
                .topicItemId(protectedId).status(ItemProgressStatus.IN_PROGRESS).build());
        mvc.perform(delete(BASE + "/{id}/items/{itemId}", topic.getId(), protectedId)
                        .param("revision", "3").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_DELETE_BLOCKED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Hide")));
        assertThat(items.findById(protectedId)).isPresent();
    }

    @Test
    void refusesReorderForPublishedTopicsAndStaleRevisionAndForeignItems() throws Exception {
        Topic topic = topic();
        String itemId = create(topic, 0, "Item", 1);
        topic = topics.findById(topic.getId()).orElseThrow();
        topic.setLatestPublishedVersion("release");
        topics.save(topic);

        mvc.perform(patch(BASE + "/{id}/items/order", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"itemIds\":[\"" + itemId + "\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(BASE + "/{id}/items/{itemId}", topic.getId(), itemId)
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"title\":\"Stale\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(BASE + "/{id}/items/{itemId}/visibility", topic.getId(), "foreign")
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"hidden\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/{id}/items", topic.getId()).with(role(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"title\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesVisibilityChangesWhenPublishedAndWorkingHistoriesAreAmbiguous() throws Exception {
        Topic topic = topic();
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion published = version(topic, item, 1, 1, ContentStatus.PUBLISHED);
        TopicItemVersion working = version(topic, item, 2, 1, ContentStatus.DRAFT);
        TopicVersion release = releases.save(TopicVersion.builder().topicId(topic.getId()).version(1)
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of(published.getId())).build());
        topic.setLatestPublishedVersion(release.getId());
        topics.save(topic);

        mvc.perform(patch(BASE + "/{id}/items/{itemId}/visibility", topic.getId(), item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"hidden\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_VISIBILITY_BLOCKED"));

        working.setStatus(ContentStatus.HIDDEN);
        working.setTopicVersionId("historical-release-marker");
        versions.save(working);
        topic = topics.findById(topic.getId()).orElseThrow();
        topic.setLatestPublishedVersion(null);
        topics.save(topic);
        mvc.perform(patch(BASE + "/{id}/items/{itemId}/visibility", topic.getId(), item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"hidden\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_VISIBILITY_BLOCKED"));
    }

    @Test
    void refusesDeleteWhenVersionIsReferencedByReleaseOrAttempt() throws Exception {
        Topic topic = topic();
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion version = version(topic, item, 1, 1, ContentStatus.DRAFT);
        releases.save(TopicVersion.builder().topicId(topic.getId()).version(1)
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of(version.getId())).build());
        attempts.save(UserTopicItemAttempt.builder().userId("learner").topicId(topic.getId())
                .topicItemId(item.getId()).attemptType(AttemptType.LEARNING).status(AttemptStatus.IN_PROGRESS).build());

        mvc.perform(delete(BASE + "/{id}/items/{itemId}", topic.getId(), item.getId())
                        .param("revision", "0").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isConflict());
        assertThat(items.findById(item.getId())).isPresent();
        assertThat(versions.findById(version.getId())).isPresent();
    }

    @Test
    void refusesDeleteWhenAnItemOnlyAppearsInARetrySnapshot() throws Exception {
        Topic topic = topic();
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion version = version(topic, item, 1, 1, ContentStatus.DRAFT);
        var pin = fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo.builder()
                .topicItemId(item.getId()).topicItemVersionId(version.getId()).build();
        mongoTemplate.save(fit.iuh.engolearn.topic.model.TopicRetryRuntime.builder()
                .userId("learner").topicId(topic.getId()).itemsSnapshot(List.of(pin)).build());

        mvc.perform(delete(BASE + "/{id}/items/{itemId}", topic.getId(), item.getId())
                        .param("revision", "0").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_DELETE_BLOCKED"));
        assertThat(versions.findById(version.getId())).isPresent();
    }

    private Topic topic() {
        return topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch").revision(0L).build());
    }

    private String create(Topic topic, long revision, String title, int expectedCount) throws Exception {
        var response = mvc.perform(post(BASE + "/{id}/items", topic.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + revision + ",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision").value(revision + 1))
                .andExpect(jsonPath("$.data.items.length()").value(expectedCount))
                .andReturn().getResponse().getContentAsString();
        int lastIndex = objectMapper.readTree(response).path("data").path("items").size() - 1;
        return objectMapper.readTree(response).path("data").path("items").get(lastIndex).path("id").asText();
    }

    private TopicItemVersion version(Topic topic, TopicItem item, int versionNumber, int order, ContentStatus status) {
        return versions.save(TopicItemVersion.builder().topicId(topic.getId()).topicItemId(item.getId())
                .version(versionNumber).order(order).status(status).title("Item " + order).build());
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("editor", "editor@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
