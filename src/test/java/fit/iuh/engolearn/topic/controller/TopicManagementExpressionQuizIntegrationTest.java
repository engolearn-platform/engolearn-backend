package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
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
class TopicManagementExpressionQuizIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics/{topicId}/items/{itemId}";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private TopicItemVersionRepository versions;
    @Autowired private TopicQuizMapper quizMapper;

    @Test
    void persistsCompleteExpressionAndQuizSectionsAndKeepsLearnerQuizResponseAnswerFree() throws Exception {
        Fixture f = fixture();
        mvc.perform(put(BASE + "/expressions", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"revision":0,"expressions":[{"tips":"Keep it natural","purposes":[{"purposeEn":"Ask politely","purposeVi":"Hỏi lịch sự","orderIndex":2,"groupType":"COMMON","examples":[{"type":"POLITE_INQUIRY","label":"POLITE","textEn":"Could you help?","textVi":"Bạn giúp tôi được không?","usageNote":"Use could","audioUrl":"speech.mp3","linkedVocabIds":["hotel"]}]}]}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.item.expressions[0].purposes[0].groupType").value("COMMON"))
                .andExpect(jsonPath("$.data.item.expressions[0].purposes[0].examples[0].label").value("POLITE"));
        String quizJson = """
                {"revision":1,"quizzes":[
                  {"quizId":"fill-module","quizType":"FILL_BLANK","title":"Fill","instructions":"Choose","orderIndex":0,"questions":[{"prompt":"Complete","options":[{"label":"A","textEn":"buy","isCorrect":false},{"label":"B","textEn":"reserve","isCorrect":true},{"label":"C","textEn":"take","isCorrect":false},{"label":"D","textEn":"make","isCorrect":false}],"correct":"B","sentence":{"textEn":"I want to reserve a room."},"answerData":{"secret":"reserve"},"explanation":"Fits the context"}]},
                  {"quizId":"situation-module","quizType":"SITUATIONAL_CHOICE","title":"Situation","orderIndex":1,"questions":[{"prompt":"At reception?","options":[{"label":"A","textEn":"Could I check in?","isCorrect":true},{"label":"B","textEn":"No","isCorrect":false},{"label":"C","textEn":"Bye","isCorrect":false},{"label":"D","textEn":"Later","isCorrect":false}],"explanation":"A polite request"}]}
                ]}
                """;
        mvc.perform(put(BASE + "/quizzes", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(quizJson))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.item.quizzes[0].questions[0].answerData.secret").value("reserve"))
                .andExpect(jsonPath("$.data.item.quizzes[1].questions[0].options[0].isCorrect").value(true));

        TopicItemVersion saved = versions.findById(f.version.getId()).orElseThrow();
        assertThat(saved.getExpressions().getFirst().getPurposes().getFirst().getExamples().getFirst().getLinkedVocabIds())
                .containsExactly("hotel");
        assertThat(saved.getQuizzes()).hasSize(2);
        var learnerQuiz = quizMapper.toDto(quizMapper.sequence(saved).getFirst());
        assertThat(learnerQuiz).hasFieldOrProperty("options");
        assertThat(learnerQuiz.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("correct", "answerData", "isCorrect");
    }

    @Test
    void allowsIncompleteDraftsAndRejectsInvalidReferencesQuizTypesDuplicateLabelsAndStaleOrPublishedWrites() throws Exception {
        Fixture f = fixture();
        mvc.perform(put(BASE + "/expressions", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"expressions\":[{}]}"))
                .andExpect(status().isOk());
        mvc.perform(put(BASE + "/expressions", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"expressions\":[{\"purposes\":[{\"examples\":[{\"linkedVocabIds\":[\"foreign\"]}]}]}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/quizzes", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"quizzes\":[{\"quizType\":\"MATCHING\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/quizzes", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1,\"quizzes\":[{\"quizType\":\"FILL_BLANK\",\"questions\":[{\"options\":[{\"label\":\"A\"},{\"label\":\"a\"}]}]}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/quizzes", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"quizzes\":[]}"))
                .andExpect(status().isConflict());
        TopicItemVersion published = versions.findById(f.version.getId()).orElseThrow();
        published.setStatus(ContentStatus.PUBLISHED);
        versions.save(published);
        mvc.perform(put(BASE + "/expressions", f.topic.getId(), f.item.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"expressions\":[]}"))
                .andExpect(status().isConflict());
        assertThat(versions.findById(f.version.getId()).orElseThrow().getExpressions()).hasSize(1);
    }

    private Fixture fixture() {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch").revision(0L).build());
        TopicItem item = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion version = versions.save(TopicItemVersion.builder().topicId(topic.getId()).topicItemId(item.getId())
                .version(1).order(1).status(ContentStatus.DRAFT)
                .vocabularies(List.of(ItemWordVo.builder().vocabId("hotel").senseId("hotel-sense")
                        .snapshot(WordSnapshotVo.builder().word("hotel").build()).build())).build());
        return new Fixture(topic, item, version);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("editor", "editor@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record Fixture(Topic topic, TopicItem item, TopicItemVersion version) {}
}
