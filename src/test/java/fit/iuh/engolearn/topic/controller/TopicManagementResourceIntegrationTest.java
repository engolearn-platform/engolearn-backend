package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicManagementResourceIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics/{topicId}/items/{itemId}";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private TopicItemVersionRepository versions;
    @Autowired private TopicVersionRepository releases;
    @Autowired private MongoTemplate mongo;

    @Test
    void replacesAndClearsContextWithoutChangingVocabularyOrExpressions() throws Exception {
        Fixture f = fixture();
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"context\":{\"prompt\":\"At a hotel\",\"scenarioDescription\":\"Check in\",\"dialogue\":[{\"speaker\":\"A\",\"textEn\":\"Hello\",\"textVi\":\"Xin chào\",\"audioUrl\":\"audio\"}],\"durationSec\":60,\"tips\":\"Smile\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.item.context.dialogue[0].audioUrl").value("audio"))
                .andExpect(jsonPath("$.data.item.vocabularies[0].snapshot.audioUsUrl").value("us"));
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"context\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.context").isEmpty())
                .andExpect(jsonPath("$.data.item.expressions[0].tips").value("keep"));
        TopicItemVersion saved = versions.findById(f.version.getId()).orElseThrow();
        assertThat(saved.getContext()).isNull();
        assertThat(saved.getVocabularies()).hasSize(2);
    }

    @Test
    void replacesVocabularyInOrderAndRejectsDeletingExpressionLinkedReference() throws Exception {
        Fixture f = fixture();
        String reordered = "{\"revision\":0,\"vocabularies\":["
                + "{\"vocabId\":\"legacy-b\",\"senseId\":\"sense-b\",\"snapshot\":{\"word\":\"Second\",\"audioUsUrl\":\"second-us\"},\"samples\":[{\"textEn\":\"sample\",\"textVi\":\"ví dụ\",\"audioUrl\":\"sample-audio\",\"isManual\":true}],\"collocations\":[],\"quickCheck\":{\"sentence\":{\"textEn\":\"Quick\",\"audioUrlSlow\":\"slow\"},\"quizData\":{\"x\":1},\"answerData\":{\"y\":2},\"explanation\":\"why\"},\"orderIndex\":2,\"tips\":\"second tip\"},"
                + "{\"vocabId\":\"legacy-a\",\"senseId\":\"sense-a\",\"snapshot\":{\"word\":\"First\"},\"samples\":[],\"collocations\":[{\"text\":\"take a break\",\"example\":{\"en\":\"We rest\",\"vi\":\"Nghỉ\"},\"descriptionVi\":\"idiom\"}],\"orderIndex\":1,\"tips\":\"first tip\"}]}";
        mvc.perform(put(BASE + "/vocabulary", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(reordered))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.vocabularies[0].vocabId").value("legacy-b"))
                .andExpect(jsonPath("$.data.item.vocabularies[0].samples[0].isManual").value(true))
                .andExpect(jsonPath("$.data.item.vocabularies[0].quickCheck.sentence.audioUrlSlow").value("slow"))
                .andExpect(jsonPath("$.data.item.vocabularies[1].collocations[0].example.vi").value("Nghỉ"));
        mvc.perform(put(BASE + "/vocabulary", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"vocabularies\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/vocabulary", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"vocabularies\":[{\"vocabId\":\"new\",\"senseId\":\"new-sense\"}]}"))
                .andExpect(status().isBadRequest());
        assertThat(versions.findById(f.version.getId()).orElseThrow().getVocabularies()).hasSize(2);
    }

    @Test
    void rejectsMissingSectionStaleForeignPublishedAndUnauthorizedMutations() throws Exception {
        Fixture f = fixture();
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/vocabulary", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"context\":null}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/context", f.topic.getId(), "foreign").with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"context\":null}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2,\"context\":null}"))
                .andExpect(status().isConflict());
        TopicItemVersion published = versions.findById(f.version.getId()).orElseThrow();
        published.setStatus(ContentStatus.PUBLISHED);
        versions.save(published);
        mvc.perform(put(BASE + "/context", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"context\":null}"))
                .andExpect(status().isConflict());
    }

    @Test
    void minorAppliesSafeVocabularyCorrectionInPlaceAndKeepsReleaseIdentity() throws Exception {
        Fixture f = publishedFixture();
        java.time.Instant originalPublishedAt = versions.findById(f.version.getId()).orElseThrow().getPublishedAt();
        String body = "{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"vocabularies\":["
                + "{\"vocabId\":\"legacy-a\",\"senseId\":\"sense-a\",\"snapshot\":{\"word\":\"First corrected\",\"audioUsUrl\":\"us\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null},"
                + "{\"vocabId\":\"legacy-b\",\"senseId\":\"sense-b\",\"snapshot\":{\"word\":\"Second\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(5))
                .andExpect(jsonPath("$.data.item.versionId").value(f.version.getId()))
                .andExpect(jsonPath("$.data.item.vocabularies[0].snapshot.word").value("First corrected"));
        TopicItemVersion saved = versions.findById(f.version.getId()).orElseThrow();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getTopicVersionId()).isEqualTo(f.version.getTopicVersionId());
        assertThat(saved.getPublishedAt()).isEqualTo(originalPublishedAt);
        assertThat(topics.findById(f.topic.getId()).orElseThrow().getLatestPublishedVersion()).isEqualTo(f.version.getTopicVersionId());
        assertThat(releases.findById(f.version.getTopicVersionId()).orElseThrow().getItemVersionIds()).containsExactly(f.version.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void minorRejectsSameCountReorderAndWrongTargetVersion() throws Exception {
        Fixture f = publishedFixture();
        String reordered = "{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"vocabularies\":["
                + "{\"vocabId\":\"legacy-b\",\"senseId\":\"sense-b\",\"snapshot\":{\"word\":\"Second\"},\"samples\":[],\"collocations\":[],\"tips\":null},"
                + "{\"vocabId\":\"legacy-a\",\"senseId\":\"sense-a\",\"snapshot\":{\"word\":\"First\"},\"samples\":[],\"collocations\":[],\"tips\":null}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(reordered))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MINOR_REQUIRES_MAJOR"));
        String changedOption = "{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"quizzes\":[{"
                + "\"quizId\":\"quiz-1\",\"quizType\":\"SITUATIONAL_CHOICE\",\"title\":\"Choose\",\"instructions\":\"Select an answer\",\"orderIndex\":1,\"questions\":[{"
                + "\"prompt\":\"Pick one\",\"correct\":\"A\",\"explanation\":\"Why\",\"quizData\":{\"round\":1},\"answerData\":{\"answer\":\"A\"},"
                + "\"options\":[{\"label\":\"A\",\"textEn\":\"changed\",\"isCorrect\":true},{\"label\":\"B\",\"textEn\":\"second\",\"isCorrect\":false}]}]}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(changedOption))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MINOR_REQUIRES_MAJOR"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":4,\"itemVersionId\":\"stale\",\"context\":{}}"))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":3,\"itemVersionId\":\"" + f.version.getId() + "\",\"context\":{}}"))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"context\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void minorRejectsForeignManagedAudioWithoutChangingItemOrRevision() throws Exception {
        Fixture f = publishedFixture();
        String body = "{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"vocabularies\":["
                + "{\"vocabId\":\"legacy-a\",\"senseId\":\"sense-a\",\"snapshot\":{\"word\":\"First\",\"audioUsUrl\":\"topic-media:foreign:media-1\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null},"
                + "{\"vocabId\":\"legacy-b\",\"senseId\":\"sense-b\",\"snapshot\":{\"word\":\"Second\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(topics.findById(f.topic.getId()).orElseThrow().getRevision()).isEqualTo(4L);
        assertThat(versions.findById(f.version.getId()).orElseThrow().getVocabularies().getFirst()
                .getSnapshot().getAudioUsUrl()).isEqualTo("us");
    }

    @Test
    void minorLeavesInProgressLearnerDocumentsUntouched() throws Exception {
        Fixture f = publishedFixture();
        var now = java.time.Instant.now();
        UserTopicAttempt topicAttempt = mongo.save(UserTopicAttempt.builder().userId("learner").topicId(f.topic.getId())
                .attemptType(fit.iuh.engolearn.topic.model.enums.AttemptType.LEARNING)
                .scope(fit.iuh.engolearn.topic.model.enums.AttemptScope.ITEM).topicVersionId(f.version.getTopicVersionId())
                .targetTopicItemId(f.item.getId()).currentTopicItemId(f.item.getId())
                .status(fit.iuh.engolearn.topic.model.enums.AttemptStatus.IN_PROGRESS).startedAt(now).build());
        UserTopicItemAttempt itemAttempt = mongo.save(UserTopicItemAttempt.builder().generation(0)
                .attemptType(fit.iuh.engolearn.topic.model.enums.AttemptType.LEARNING).userTopicAttemptId(topicAttempt.getId())
                .userId("learner").topicId(f.topic.getId()).topicItemId(f.item.getId()).topicItemVersionId(f.version.getId())
                .status(fit.iuh.engolearn.topic.model.enums.AttemptStatus.IN_PROGRESS).startedAt(now).build());
        UserTopicItemProgress progress = mongo.save(UserTopicItemProgress.builder().generation(0).userId("learner")
                .topicId(f.topic.getId()).topicItemId(f.item.getId())
                .status(fit.iuh.engolearn.topic.model.enums.ItemProgressStatus.IN_PROGRESS).lastAccessedAt(now).build());
        UserTopicAttempt persistedTopicAttempt = mongo.findById(topicAttempt.getId(), UserTopicAttempt.class);
        UserTopicItemAttempt persistedItemAttempt = mongo.findById(itemAttempt.getId(), UserTopicItemAttempt.class);
        UserTopicItemProgress persistedProgress = mongo.findById(progress.getId(), UserTopicItemProgress.class);
        String body = "{\"revision\":4,\"itemVersionId\":\"" + f.version.getId() + "\",\"vocabularies\":["
                + "{\"vocabId\":\"legacy-a\",\"senseId\":\"sense-a\",\"snapshot\":{\"word\":\"First corrected\",\"audioUsUrl\":\"us\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null},"
                + "{\"vocabId\":\"legacy-b\",\"senseId\":\"sense-b\",\"snapshot\":{\"word\":\"Second\"},\"samples\":[],\"collocations\":[],\"orderIndex\":null,\"tips\":null}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE + "/minor", f.topic.getId(), f.item.getId())
                        .with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(mongo.findById(topicAttempt.getId(), UserTopicAttempt.class)).isEqualTo(persistedTopicAttempt);
        assertThat(mongo.findById(itemAttempt.getId(), UserTopicItemAttempt.class)).isEqualTo(persistedItemAttempt);
        assertThat(mongo.findById(progress.getId(), UserTopicItemProgress.class)).isEqualTo(persistedProgress);
    }

    private Fixture publishedFixture() {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch").revision(4L).build());
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        ItemWordVo first = ItemWordVo.builder().vocabId("legacy-a").senseId("sense-a")
                .snapshot(WordSnapshotVo.builder().word("First").audioUsUrl("us").build()).build();
        ItemWordVo second = ItemWordVo.builder().vocabId("legacy-b").senseId("sense-b")
                .snapshot(WordSnapshotVo.builder().word("Second").build()).build();
        QuizModuleVo quiz = QuizModuleVo.builder().quizId("quiz-1").quizType(fit.iuh.engolearn.topic.model.enums.QuizType.SITUATIONAL_CHOICE)
                .title("Choose").instructions("Select an answer").orderIndex(1).questions(List.of(QuizQuestionVo.builder()
                        .prompt("Pick one").correct("A").explanation("Why").quizData(java.util.Map.of("round", 1))
                        .answerData(java.util.Map.of("answer", "A")).options(List.of(
                                QuizOptionVo.builder().label("A").textEn("first").isCorrect(true).build(),
                                QuizOptionVo.builder().label("B").textEn("second").isCorrect(false).build())).build())).build();
        TopicItemVersion version = versions.save(TopicItemVersion.builder().topicId(topic.getId()).topicItemId(item.getId())
                .version(1).order(1).status(ContentStatus.DRAFT).vocabularies(List.of(first, second)).quizzes(List.of(quiz)).build());
        TopicVersion release = releases.save(TopicVersion.builder().topicId(topic.getId()).version(1).status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of(version.getId())).publishedAt(java.time.Instant.now()).build());
        version.setStatus(ContentStatus.PUBLISHED);
        version.setTopicVersionId(release.getId());
        version.setPublishedAt(release.getPublishedAt());
        version = versions.save(version);
        topic.setLatestPublishedVersion(release.getId());
        topic = topics.save(topic);
        return new Fixture(topic, item, version);
    }

    private Fixture fixture() {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch").revision(0L).build());
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        ItemWordVo legacy = ItemWordVo.builder().vocabId("legacy-a").senseId("sense-a")
                .snapshot(WordSnapshotVo.builder().word("First").audioUsUrl("us").build()).build();
        ItemWordVo otherLegacy = ItemWordVo.builder().vocabId("legacy-b").senseId("sense-b")
                .snapshot(WordSnapshotVo.builder().word("Second").build()).build();
        ExpressionExampleVo example = ExpressionExampleVo.builder().linkedVocabIds(List.of("legacy-a")).build();
        TopicItemVersion version = versions.save(TopicItemVersion.builder().topicId(topic.getId()).topicItemId(item.getId())
                .version(1).order(1).status(ContentStatus.DRAFT).vocabularies(List.of(legacy, otherLegacy))
                .expressions(List.of(ExpressionBlockVo.builder().tips("keep")
                        .purposes(List.of(ExpressionPurposeVo.builder().examples(List.of(example)).build())).build())).build());
        return new Fixture(topic, item, version);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("editor", "editor@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record Fixture(Topic topic, TopicItem item, TopicItemVersion version) {}
}
