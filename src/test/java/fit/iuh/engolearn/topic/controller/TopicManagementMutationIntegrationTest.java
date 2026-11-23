package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicManagementMutationIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicVersionRepository topicVersions;

    @Test
    void createsIncompleteTopicDraftWithRevisionAndLocation() throws Exception {
        mvc.perform(post(BASE).with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleEn\":\"  Travel  \",\"titleVi\":\" Du lịch \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.titleEn").value("Travel"))
                .andExpect(jsonPath("$.data.titleVi").value("Du lịch"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.revision").value(0))
                .andExpect(jsonPath("$.data.durationMinutes").value(0));
        assertThat(topics.findAll()).hasSize(1);
        assertThat(topicVersions.count()).isZero();
    }

    @Test
    void patchDistinguishesAbsentFromNullAndRejectsStaleRevision() throws Exception {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch")
                .description("description").tags(List.of("starter")).build());
        mongoTemplate.getCollection("topics").updateOne(
                new org.bson.Document("_id", topic.getId()),
                new org.bson.Document("$unset", new org.bson.Document("revision", "")));

        mvc.perform(patch(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"description\":null,\"tags\":[\"updated\"],\"cefr\":\"B1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").isEmpty())
                .andExpect(jsonPath("$.data.tags[0]").value("updated"))
                .andExpect(jsonPath("$.data.cefr").value("B1"))
                .andExpect(jsonPath("$.data.revision").value(1));
        assertThat(topics.findById(topic.getId()).orElseThrow().getTitleEn()).isEqualTo("Travel");

        mvc.perform(patch(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"titleEn\":\"Stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titleEn").value("Travel"))
                .andExpect(jsonPath("$.data.revision").value(1));
    }

    @Test
    void rejectsComputedFieldsAndLearnerMutations() throws Exception {
        mvc.perform(post(BASE).with(role(UserRole.CONTENT_MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleEn\":\"Travel\",\"titleVi\":\"Du lịch\",\"durationMinutes\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).with(role(UserRole.USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleEn\":\"Travel\",\"titleVi\":\"Du lịch\"}"))
                .andExpect(status().isForbidden());
        assertThat(topics.count()).isZero();
    }

    @Test
    void publishedTopicMetadataPatchDoesNotCreateReleaseHistory() throws Exception {
        Topic topic = topics.save(Topic.builder().titleEn("Travel").titleVi("Du lịch")
                .latestPublishedVersion("release-1").build());
        topicVersions.save(TopicVersion.builder().id("release-1").topicId(topic.getId()).version(1)
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of()).build());

        mvc.perform(patch(BASE + "/{id}", topic.getId()).with(role(UserRole.CONTENT_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0,\"titleEn\":\"Travel Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.revision").value(1));
        assertThat(topicVersions.count()).isEqualTo(1);
        assertThat(topics.findById(topic.getId()).orElseThrow().getLatestPublishedVersion()).isEqualTo("release-1");
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("editor", "editor@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
