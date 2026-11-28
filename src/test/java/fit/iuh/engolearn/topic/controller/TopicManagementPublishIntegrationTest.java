package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionGroupType;
import fit.iuh.engolearn.topic.model.enums.ExpressionLabel;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicManagementPublishIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private TopicItemVersionRepository itemVersions;
    @Autowired private TopicVersionRepository topicVersions;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private MongoTemplate mongoTemplateSpy;

    @Test
    void publishesOneOrderedInitialReleaseAndMakesItVisibleToGuests() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> candidates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) candidates.add(seedCompleteItem(topic, i));
        TopicItem hidden = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion hiddenVersion = itemVersions.save(TopicItemVersion.builder().topicId(topic.getId())
                .topicItemId(hidden.getId()).version(1).order(6).status(ContentStatus.HIDDEN).build());

        mvc.perform(get("/api/v1/topics/{id}", topic.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value(topic.getId()))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.itemCount").value(5))
                .andExpect(jsonPath("$.data.durationMinutes").value(15));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict());

        Topic published = topics.findById(topic.getId()).orElseThrow();
        assertThat(published.getLatestPublishedVersion()).isNotBlank();
        assertThat(published.getRevision()).isEqualTo(1);
        assertThat(published.getDurationMinutes()).isEqualTo(15);
        var release = topicVersions.findById(published.getLatestPublishedVersion()).orElseThrow();
        assertThat(release.getVersion()).isEqualTo(1);
        assertThat(release.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(release.getItemVersionIds()).containsExactlyElementsOf(
                candidates.stream().map(TopicItemVersion::getId).toList());
        for (TopicItemVersion original : candidates) {
            var stored = itemVersions.findById(original.getId()).orElseThrow();
            assertThat(stored.getVersion()).isEqualTo(1);
            assertThat(stored.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
            assertThat(stored.getTopicVersionId()).isEqualTo(release.getId());
            assertThat(stored.getPublishedAt()).isEqualTo(release.getPublishedAt());
        }
        assertThat(itemVersions.findById(hiddenVersion.getId()).orElseThrow().getStatus()).isEqualTo(ContentStatus.HIDDEN);
        mvc.perform(get("/api/v1/topics/{id}", topic.getId())).andExpect(status().isOk());
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("topic_speaking_profiles").countDocuments()).isZero();
    }

    @Test
    void invalidFourItemCandidateReturnsViolationsAndWritesNoRelease() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> candidates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) candidates.add(seedCompleteItem(topic, i));
        topic.setCoverUrl(media(topic, topic.getMediaReferences().get(1).getMediaId()));
        topics.save(topic);
        TopicItemVersion first = itemVersions.findById(candidates.getFirst().getId()).orElseThrow();
        first.getVocabularies().getFirst().getSnapshot().setAudioUsUrl(null);
        itemVersions.save(first);

        MvcResult readiness = mvc.perform(get(BASE + "/{id}/readiness", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andReturn();
        MvcResult publish = mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].path").value("topic.coverUrl"))
                .andExpect(jsonPath("$.violations[0].code").value("INVALID_MEDIA"))
                .andExpect(jsonPath("$.violations[1].path").value("topic.items"))
                .andExpect(jsonPath("$.violations[2].path").value(
                        "items[" + candidates.getFirst().getTopicItemId() + "].vocabularies[0].snapshot.audioUsUrl"))
                .andReturn();
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode readinessViolations = mapper.readTree(readiness.getResponse().getContentAsString()).get("data").get("violations");
        JsonNode publishViolations = mapper.readTree(publish.getResponse().getContentAsString()).get("violations");
        assertThat(publishViolations).isEqualTo(readinessViolations);
        assertThat(topicVersions.count()).isZero();
        assertThat(topics.findById(topic.getId()).orElseThrow().getRevision()).isZero();
        assertThat(topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion()).isNull();
        assertThat(itemVersions.findAll().stream().allMatch(item -> item.getStatus() == ContentStatus.DRAFT)).isTrue();
    }

    @Test
    void optionalQuickCheckSentenceAudioViolationsMatchReadinessAndPublishWithoutWrites() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> candidates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) candidates.add(seedCompleteItem(topic, i));
        TopicItemVersion first = itemVersions.findById(candidates.getFirst().getId()).orElseThrow();
        var word = first.getVocabularies().getFirst();
        String imageReference = media(topic, topic.getMediaReferences().getFirst().getMediaId());
        word.setQuickCheck(QuickCheckVo.builder().sentence(QuizSentenceVo.builder().textEn("I need a ticket.")
                .audioUrl(imageReference).audioUrlSlow("topic-media:another-topic:foreign-audio").build()).build());
        itemVersions.save(first);

        MvcResult readiness = mvc.perform(get(BASE + "/{id}/readiness", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andReturn();
        MvcResult publish = mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].path").value(org.hamcrest.Matchers.hasItems(
                        "items[" + first.getTopicItemId() + "].vocabularies[0].quickCheck.sentence.audioUrl",
                        "items[" + first.getTopicItemId() + "].vocabularies[0].quickCheck.sentence.audioUrlSlow")))
                .andReturn();
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode readinessViolations = mapper.readTree(readiness.getResponse().getContentAsString()).get("data").get("violations");
        JsonNode publishViolations = mapper.readTree(publish.getResponse().getContentAsString()).get("violations");
        assertThat(publishViolations).isEqualTo(readinessViolations);
        assertThat(topicVersions.count()).isZero();
        Topic stored = topics.findById(topic.getId()).orElseThrow();
        assertThat(stored.getRevision()).isZero();
        assertThat(stored.getLatestPublishedVersion()).isNull();
        assertThat(itemVersions.findAll().stream().allMatch(item -> item.getStatus() == ContentStatus.DRAFT)).isTrue();
    }

    @Test
    void twentyOneVisibleItemsAreRejectedWithoutCreatingARelease() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 21; i++) seedCompleteItem(topic, i);

        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].path").value("topic.items"));
        assertThat(topicVersions.count()).isZero();
        assertThat(topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion()).isNull();
    }

    @Test
    void staleAndUnauthorizedPublishRequestsDoNotWrite() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        topic.setRevision(1L);
        topics.save(topic);
        assertThat(topics.findById(topic.getId()).orElseThrow().getRevision()).isEqualTo(1L);

        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isForbidden());
        assertThat(topicVersions.count()).isZero();
        assertThat(itemVersions.findAll().stream().allMatch(item -> item.getStatus() == ContentStatus.DRAFT)).isTrue();
    }

    @Test
    void simultaneousRequestsWithOneRevisionCommitAtMostOneRelease() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Publish race was not released.");
                return mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(topicVersions.count()).isEqualTo(1);
            Topic stored = topics.findById(topic.getId()).orElseThrow();
            var release = topicVersions.findById(stored.getLatestPublishedVersion()).orElseThrow();
            assertThat(release.getItemVersionIds()).hasSize(5);
            assertThat(itemVersions.findAll().stream().filter(item -> item.getStatus() == ContentStatus.PUBLISHED)).hasSize(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void transactionFailureAfterCandidateWritesRollsBackEveryReleaseChange() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected publish failure after Topic CAS.");
        }).when(mongoTemplateSpy).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Topic.class));
        assertThatThrownBy(() -> mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(topicVersions.count()).isZero();
        Topic stored = topics.findById(topic.getId()).orElseThrow();
        assertThat(stored.getLatestPublishedVersion()).isNull();
        assertThat(stored.getRevision()).isZero();
        assertThat(stored.getDurationMinutes()).isNull();
        assertThat(itemVersions.findAll().stream().allMatch(item -> item.getStatus() == ContentStatus.DRAFT)).isTrue();
        assertThat(itemVersions.findAll().stream().allMatch(item -> item.getTopicVersionId() == null
                && item.getPublishedAt() == null)).isTrue();
    }

    @Test
    void mixedBatchFailureAfterTopicCasRestoresPromotedAndRetiredDrafts() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        Topic before = topics.findById(topic.getId()).orElseThrow();
        var releaseBefore = topicVersions.findById(before.getLatestPublishedVersion()).orElseThrow();
        List<String> oldManifest = List.copyOf(releaseBefore.getItemVersionIds());

        TopicItemVersion noOp = itemVersions.save(majorDraft(initial.getFirst()));
        TopicItemVersion changed = majorDraft(initial.get(1));
        changed.setTitle("Changed title");
        changed = itemVersions.save(changed);
        TopicItemVersion changedBefore = changed;
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected mixed-batch failure after Topic CAS.");
        }).when(mongoTemplateSpy).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Topic.class));

        assertThatThrownBy(() -> mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        Topic after = topics.findById(topic.getId()).orElseThrow();
        assertThat(after.getRevision()).isEqualTo(before.getRevision());
        assertThat(after.getLatestPublishedVersion()).isEqualTo(before.getLatestPublishedVersion());
        assertThat(after.getDurationMinutes()).isEqualTo(before.getDurationMinutes());
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topicVersions.findById(releaseBefore.getId()).orElseThrow().getItemVersionIds()).isEqualTo(oldManifest);
        assertThat(itemVersions.findById(changed.getId()).orElseThrow())
                .usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(changedBefore);
        assertThat(itemVersions.findById(noOp.getId())).isPresent();
        assertThat(itemVersions.findById(noOp.getId()).orElseThrow())
                .usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(noOp);
    }

    @Test
    void mixedBatchRemovesOnlyUnpublishedNoopMajorAndLeavesVersionAvailable() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        Topic releasedTopic = topics.findById(topic.getId()).orElseThrow();
        var oldRelease = topicVersions.findById(releasedTopic.getLatestPublishedVersion()).orElseThrow();

        TopicItemVersion noOp = majorDraft(initial.getFirst());
        itemVersions.save(noOp);
        TopicItemVersion changed = majorDraft(initial.get(1));
        changed.setTitle("Changed title");
        itemVersions.save(changed);

        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.changes.length()").value(1))
                .andExpect(jsonPath("$.data.changes[0].topicItemId").value(changed.getTopicItemId()));

        assertThat(itemVersions.existsById(noOp.getId())).isFalse();
        assertThat(itemVersions.findAllByTopicIdAndTopicItemId(topic.getId(), initial.getFirst().getTopicItemId()))
                .extracting(TopicItemVersion::getVersion).containsExactly(1);
        assertThat(itemVersions.findById(changed.getId()).orElseThrow().getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        var currentRelease = topicVersions.findById(topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion()).orElseThrow();
        assertThat(currentRelease.getPreviousVersionId()).isEqualTo(oldRelease.getId());
        assertThat(currentRelease.getItemVersionIds()).contains(initial.getFirst().getId(), changed.getId());

        TopicItemVersion next = majorDraft(itemVersions.findById(changed.getId()).orElseThrow());
        assertThat(next.getVersion()).isEqualTo(3);
    }

    @Test
    void noOpOnlyBatchReturnsStableConflictAndLeavesDraftForReopen() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        TopicItemVersion draft = majorDraft(initial.getFirst());
        itemVersions.save(draft);

        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_EFFECTIVE_CHANGES"));

        assertThat(itemVersions.existsById(draft.getId())).isTrue();
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topics.findById(topic.getId()).orElseThrow().getRevision()).isEqualTo(1L);
    }

    @Test
    void unpublishSuspendsManagerStatusAndRetainsThePublishedReleaseHistory() throws Exception {
        Topic topic = newTopic();
        topic.setTitleEn("P04 Airport Assistance");
        topics.save(topic);
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        Topic published = topics.findById(topic.getId()).orElseThrow();
        String releaseId = published.getLatestPublishedVersion();
        var manifest = List.copyOf(topicVersions.findById(releaseId).orElseThrow().getItemVersionIds());

        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value(topic.getId()))
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.status").value("unpublished"))
                .andExpect(jsonPath("$.data.latestPublishedVersion").value(releaseId));
        mvc.perform(get(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("unpublished"))
                .andExpect(jsonPath("$.data.latestPublishedVersion").value(releaseId))
                .andExpect(jsonPath("$.data.revision").value(2));
        mvc.perform(get(BASE).with(role(UserRole.CONTENT_MANAGER)).param("status", "unpublished")
                        .param("search", "P04 Airport Assistance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("unpublished"))
                .andExpect(jsonPath("$.data.counters.unpublished").value(1));
        mvc.perform(get(BASE).with(role(UserRole.CONTENT_MANAGER)).param("status", "published")
                        .param("search", "P04 Airport Assistance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        Topic suspended = topics.findById(topic.getId()).orElseThrow();
        assertThat(suspended.getLatestPublishedVersion()).isEqualTo(releaseId);
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topicVersions.findById(releaseId).orElseThrow().getItemVersionIds()).isEqualTo(manifest);
        assertThat(itemVersions.findAllByTopicId(topic.getId())).hasSize(5)
                .allMatch(version -> version.getStatus() == ContentStatus.PUBLISHED);
    }

    @Test
    void republishWithoutChangesReusesTheExistingRelease() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        String releaseId = topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion();
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(3))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.topicVersionId").value(releaseId))
                .andExpect(jsonPath("$.data.changes.length()").value(0));
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion()).isEqualTo(releaseId);
    }

    @Test
    void unpublishRejectsUnauthorizedStaleAndAlreadySuspendedRequests() throws Exception {
        Topic topic = newTopic();
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict());
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void republishWithChangedMajorCreatesOneSuccessorAndClearsSuspension() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        String firstReleaseId = topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion();
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        TopicItemVersion changed = majorDraft(initial.getFirst());
        changed.setTitle("Changed while unpublished");
        itemVersions.save(changed);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.previousTopicVersionId").value(firstReleaseId))
                .andExpect(jsonPath("$.data.changes.length()").value(1));
        Topic republished = topics.findById(topic.getId()).orElseThrow();
        assertThat(republished.getPublicationSuspended()).isFalse();
        assertThat(republished.getRevision()).isEqualTo(3L);
        assertThat(topicVersions.count()).isEqualTo(2);
        assertThat(topicVersions.findById(republished.getLatestPublishedVersion()).orElseThrow().getPreviousVersionId())
                .isEqualTo(firstReleaseId);
    }

    @Test
    void unchangedRepublishRejectsAnIncompleteRetainedManifest() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 5; i++) seedCompleteItem(topic, i);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        String releaseId = topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion();
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        var release = topicVersions.findById(releaseId).orElseThrow();
        release.setItemVersionIds(release.getItemVersionIds().subList(1, release.getItemVersionIds().size()));
        topicVersions.save(release);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isConflict());
        Topic stillSuspended = topics.findById(topic.getId()).orElseThrow();
        assertThat(stillSuspended.getPublicationSuspended()).isTrue();
        assertThat(stillSuspended.getLatestPublishedVersion()).isEqualTo(releaseId);
        assertThat(topicVersions.count()).isEqualTo(1);
    }

    @Test
    void unchangedRepublishRejectsManifestVersionsWithDuplicateStableItemIds() throws Exception {
        Topic topic = newTopic();
        for (int i = 1; i <= 6; i++) seedCompleteItem(topic, i);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        String releaseId = topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion();
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        var release = topicVersions.findById(releaseId).orElseThrow();
        List<String> duplicateManifest = new ArrayList<>(release.getItemVersionIds());
        duplicateManifest.set(duplicateManifest.size() - 1, duplicateManifest.getFirst());
        release.setItemVersionIds(duplicateManifest);
        topicVersions.save(release);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isConflict());
        Topic stillSuspended = topics.findById(topic.getId()).orElseThrow();
        assertThat(stillSuspended.getPublicationSuspended()).isTrue();
        assertThat(stillSuspended.getLatestPublishedVersion()).isEqualTo(releaseId);
        assertThat(topicVersions.count()).isEqualTo(1);
    }

    @Test
    void failedSuspendedMajorRepublishRollsBackTopicAndReleaseChanges() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());
        Topic before = topics.findById(topic.getId()).orElseThrow();
        String releaseId = before.getLatestPublishedVersion();
        var releaseBefore = topicVersions.findById(releaseId).orElseThrow();
        List<String> manifestBefore = List.copyOf(releaseBefore.getItemVersionIds());
        TopicItemVersion changed = majorDraft(initial.getFirst());
        changed.setTitle("Changed before failed republish");
        changed = itemVersions.save(changed);
        TopicItemVersion draftBefore = changed;
        mvc.perform(post(BASE + "/{id}/unpublish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk());
        Topic suspendedBefore = topics.findById(topic.getId()).orElseThrow();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected failure after Topic CAS.");
        }).when(mongoTemplateSpy).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Topic.class));
        assertThatThrownBy(() -> mvc.perform(post(BASE + "/{id}/publish", topic.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":2}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        Topic after = topics.findById(topic.getId()).orElseThrow();
        assertThat(after.getPublicationSuspended()).isTrue();
        assertThat(after.getRevision()).isEqualTo(suspendedBefore.getRevision());
        assertThat(after.getLatestPublishedVersion()).isEqualTo(releaseId);
        assertThat(after.getDurationMinutes()).isEqualTo(suspendedBefore.getDurationMinutes());
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topicVersions.findById(releaseId).orElseThrow().getItemVersionIds()).isEqualTo(manifestBefore);
        assertThat(itemVersions.findById(changed.getId()).orElseThrow())
                .usingRecursiveComparison().ignoringFields("createdAt", "updatedAt").isEqualTo(draftBefore);
        assertThat(itemVersions.findById(changed.getId()).orElseThrow().getStatus()).isEqualTo(ContentStatus.DRAFT);
    }
    @Test
    void majorResourceReorderKeepsLegacyMediaButRejectsANewExternalUrl() throws Exception {
        Topic topic = newTopic();
        List<TopicItemVersion> initial = new ArrayList<>();
        for (int i = 1; i <= 5; i++) initial.add(seedCompleteItem(topic, i));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk());

        Topic published = topics.findById(topic.getId()).orElseThrow();
        published.setCoverUrl("https://example.test/legacy-cover.png");
        topics.save(published);
        TopicItemVersion unchanged = itemVersions.findById(initial.get(1).getId()).orElseThrow();
        unchanged.getVocabularies().getFirst().getSnapshot().setAudioUsUrl("https://example.test/unchanged.mp3");
        itemVersions.save(unchanged);
        TopicItemVersion base = itemVersions.findById(initial.getFirst().getId()).orElseThrow();
        base.getVocabularies().getFirst().getSnapshot().setAudioEnUrl("https://example.test/legacy.mp3");
        base.getVocabularies().getFirst().getSamples().getFirst().setAudioUrl(null);
        itemVersions.save(base);

        TopicItemVersion draft = majorDraft(base);
        var reordered = new ArrayList<>(draft.getVocabularies());
        java.util.Collections.swap(reordered, 0, 1);
        for (int i = 0; i < reordered.size(); i++) reordered.get(i).setOrderIndex(i + 1);
        draft.setVocabularies(reordered);
        itemVersions.save(draft);
        mvc.perform(get(BASE + "/{id}/readiness", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ready").value(true));

        reordered.get(1).getSnapshot().setAudioEnUrl("https://example.test/new-unmanaged.mp3");
        itemVersions.save(draft);
        mvc.perform(get(BASE + "/{id}/readiness", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.violations.length()").value(1))
                .andExpect(jsonPath("$.data.violations[0].path")
                        .value("items[" + draft.getTopicItemId() + "].vocabularies[1].snapshot.audioEnUrl"));
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isUnprocessableContent());
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topics.findById(topic.getId()).orElseThrow().getRevision()).isEqualTo(1L);

        reordered.get(1).getSnapshot().setAudioEnUrl("https://example.test/legacy.mp3");
        itemVersions.save(draft);
        mvc.perform(post(BASE + "/{id}/publish", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.changes.length()").value(1))
                .andExpect(jsonPath("$.data.changes[0].itemVersionId").value(draft.getId()));

        Topic releaseTopic = topics.findById(topic.getId()).orElseThrow();
        mvc.perform(patch(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":2,\"coverUrl\":\"https://example.test/new-unmanaged.png\"}"))
                .andExpect(status().isBadRequest());
        assertThat(releaseTopic.getCoverUrl()).isEqualTo("https://example.test/legacy-cover.png");
        assertThat(topicVersions.findById(releaseTopic.getLatestPublishedVersion()).orElseThrow().getItemVersionIds())
                .containsExactly(draft.getId(), unchanged.getId(), initial.get(2).getId(), initial.get(3).getId(), initial.get(4).getId());
        assertThat(itemVersions.findById(base.getId()).orElseThrow().getVocabularies().getFirst().getSnapshot().getWord())
                .isEqualTo("word0");
    }

    private TopicItemVersion majorDraft(TopicItemVersion base) {
        TopicItemVersion draft = objectMapper.convertValue(base, TopicItemVersion.class);
        draft.setId(null);
        draft.setCreatedAt(null);
        draft.setUpdatedAt(null);
        draft.setVersion(base.getVersion() + 1);
        draft.setPreviousItemVersionId(base.getId());
        draft.setChangeType(fit.iuh.engolearn.topic.model.enums.ChangeType.MAJOR);
        draft.setTopicVersionId(null);
        draft.setPublishedAt(null);
        draft.setStatus(ContentStatus.DRAFT);
        return draft;
    }

    private Topic newTopic() {
        String coverId = UUID.randomUUID().toString();
        String audioId = UUID.randomUUID().toString();
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch")
                .cefrLevel(CefrLevel.A1).category(TopicCategory.TRAVEL).description("Travel basics")
                .coverUrl("topic-media:pending:" + coverId).revision(0L)
                .mediaReferences(List.of(TopicMediaReferenceVo.builder().mediaId(coverId).kind(TopicMediaKind.IMAGE).build(),
                        TopicMediaReferenceVo.builder().mediaId(audioId).kind(TopicMediaKind.AUDIO).build())).build());
        topic.setCoverUrl(media(topic, coverId));
        return topics.save(topic);
    }

    private TopicItemVersion seedCompleteItem(Topic topic, int order) {
        String audio = media(topic, topic.getMediaReferences().get(1).getMediaId());
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        List<ItemWordVo> words = new ArrayList<>();
        for (int i = 0; i < 3; i++) words.add(ItemWordVo.builder().orderIndex(i + 1)
                .snapshot(WordSnapshotVo.builder().word("word" + i).phonetic("/w/").describeVi("nghĩa")
                        .audioEnUrl(audio).audioUsUrl(audio).build())
                .samples(List.of(WordSampleVo.builder().textEn("A sample").textVi("Ví dụ").audioUrl(audio).build()))
                .build());
        var example = ExpressionExampleVo.builder().type(ExpressionType.POLITE_INQUIRY)
                .textEn("Could you help?").textVi("Bạn giúp tôi?").usageNote("Polite request")
                .audioUrl(audio).label(ExpressionLabel.POLITE).build();
        var purpose = ExpressionPurposeVo.builder().groupType(ExpressionGroupType.BASIC)
                .examples(List.of(example)).build();
        var fill = QuizQuestionVo.builder().sentence(QuizSentenceVo.builder().textEn("I need an answer today.").build())
                .options(options()).explanation("Use the word").build();
        var choice = QuizQuestionVo.builder().prompt("What do you say?").options(options())
                .explanation("Choose the polite answer").build();
        var quizzes = List.of(quiz(QuizType.FILL_BLANK, fill), quiz(QuizType.SITUATIONAL_CHOICE, choice));
        return itemVersions.save(TopicItemVersion.builder().topicId(topic.getId()).topicItemId(item.getId())
                .version(1).order(order).status(ContentStatus.DRAFT).title("Item " + order)
                .description("Lesson").durationMinutes(3)
                .context(TopicContextVo.builder().scenarioDescription("At a station").build())
                .vocabularies(words).expressions(List.of(ExpressionBlockVo.builder().purposes(List.of(purpose)).build()))
                .quizzes(quizzes).build());
    }

    private static List<QuizOptionVo> options() {
        return List.of(QuizOptionVo.builder().label("A").textEn("answer").isCorrect(true).build(),
                QuizOptionVo.builder().label("B").textEn("other").isCorrect(false).build(),
                QuizOptionVo.builder().label("C").textEn("another").isCorrect(false).build(),
                QuizOptionVo.builder().label("D").textEn("last").isCorrect(false).build());
    }

    private static QuizModuleVo quiz(QuizType type, QuizQuestionVo question) {
        return QuizModuleVo.builder().quizId(type.name()).quizType(type).title(type.name())
                .questions(List.of(question, question, question)).build();
    }

    private static String media(Topic topic, String id) {
        return "topic-media:" + topic.getId() + ":" + id;
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("reader", "reader@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
