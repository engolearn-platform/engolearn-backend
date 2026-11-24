package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.QuizOptionVo;
import fit.iuh.engolearn.topic.model.vo.QuizQuestionVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicManagementReadIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private TopicVersionRepository releases;
    @Autowired private TopicItemVersionRepository versions;

    @Test
    void filtersAndReloadsAnIncompleteDraft() throws Exception {
        Topic draft = topics.save(Topic.builder().titleEn("Travel [A]").titleVi("Du lịch").build());
        topics.save(Topic.builder().titleEn("Travel A").titleVi("Khác").build());
        mvc.perform(get(BASE).with(role(UserRole.CONTENT_MANAGER)).param("search", "[a]").param("status", "draft"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(draft.getId()))
                .andExpect(jsonPath("$.data.counters.draft").value(2));
        mvc.perform(get(BASE + "/{id}", draft.getId()).with(role(UserRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.cefr").doesNotExist());
        mvc.perform(get(BASE + "/{id}/items", draft.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void selectsWorkingContentAndPreservesManifestAndHiddenStatus() throws Exception {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").build());
        Topic other = topics.save(Topic.builder().titleEn("Other").build());
        TopicItem first = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItem second = items.save(TopicItem.builder().topicId(topic.getId()).build());
        TopicItemVersion liveFirst = seed(first, 1, 1, ContentStatus.PUBLISHED, "Live first");
        TopicItemVersion liveSecond = seed(second, 1, 2, ContentStatus.PUBLISHED, "Live second");
        TopicVersion release = releases.save(TopicVersion.builder().topicId(topic.getId()).version(1)
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of(liveFirst.getId(), liveSecond.getId())).build());
        topic.setLatestPublishedVersion(release.getId());
        topic = topics.save(topic);
        TopicItemVersion draft = seed(first, 2, 1, ContentStatus.DRAFT, "Working first");
        seed(first, 3, 1, ContentStatus.BETA, "Beta first");
        TopicItemVersion hidden = seed(second, 2, 2, ContentStatus.HIDDEN, "Hidden second");
        var before = snapshot();

        mvc.perform(get(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.itemCount").value(1))
                .andExpect(jsonPath("$.data.durationMinutes").value(3));
        mvc.perform(get(BASE + "/{id}/items", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].versionId").value(draft.getId()))
                .andExpect(jsonPath("$.data[1].status").value("HIDDEN"));
        assertEmbeddedResources(topic, first, draft);
        assertEmbeddedResources(topic, second, hidden);
        mvc.perform(get(BASE + "/{id}/items/{item}", other.getId(), first.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/topics/{id}", topic.getId())).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void protectsEveryReadRoute() throws Exception {
        for (String path : List.of(BASE, BASE + "/x", BASE + "/x/items", BASE + "/x/items/y")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(role(UserRole.USER))).andExpect(status().isForbidden());
        }
    }

    private TopicItemVersion seed(TopicItem item, int version, int order, ContentStatus status, String title) {
        var question = QuizQuestionVo.builder().prompt(title).correct("B")
                .answerData(Map.of("correct", "B")).explanation(title)
                .options(List.of(QuizOptionVo.builder().label("B").textEn(title).isCorrect(true).build())).build();
        var expression = ExpressionExampleVo.builder().textEn(title).build();
        return versions.save(TopicItemVersion.builder().topicId(item.getTopicId()).topicItemId(item.getId())
                .version(version).order(order).status(status).title(title).durationMinutes(version)
                .context(TopicContextVo.builder().prompt(title).build())
                .vocabularies(List.of(ItemWordVo.builder().snapshot(WordSnapshotVo.builder().word(title).build()).build()))
                .expressions(List.of(ExpressionBlockVo.builder().purposes(List.of(ExpressionPurposeVo.builder()
                        .examples(List.of(expression)).build())).build()))
                .quizzes(List.of(QuizModuleVo.builder().quizType(QuizType.SITUATIONAL_CHOICE)
                        .questions(List.of(question)).build())).build());
    }

    private void assertEmbeddedResources(Topic topic, TopicItem item, TopicItemVersion version) throws Exception {
        String text = version.getTitle();
        mvc.perform(get(BASE + "/{id}/items/{item}", topic.getId(), item.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versionId").value(version.getId()))
                .andExpect(jsonPath("$.data.context.prompt").value(text))
                .andExpect(jsonPath("$.data.vocabularies[0].snapshot.word").value(text))
                .andExpect(jsonPath("$.data.expressions[0].purposes[0].examples[0].textEn").value(text))
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].explanation").value(text))
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].answerData.correct").value("B"))
                .andExpect(jsonPath("$.data.quizzes[0].questions[0].options[0].isCorrect").value(true));
    }

    private Map<String, List<org.bson.Document>> snapshot() {
        return List.of("topics", "topic_items", "topic_item_versions", "topic_versions").stream()
                .collect(Collectors.toMap(name -> name, name -> mongoTemplate.getCollection(name).find()
                        .sort(new org.bson.Document("_id", 1)).into(new ArrayList<>())));
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("reader", "reader@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
