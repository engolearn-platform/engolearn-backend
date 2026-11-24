package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
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
