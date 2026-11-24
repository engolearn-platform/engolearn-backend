package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EnabledIfDockerAvailable
class TopicPublishReadinessIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;

    @Test
    void readinessIsProtectedAndOnlyCountsItemsOwnedByRequestedTopic() throws Exception {
        Topic requested = topics.save(Topic.builder().titleEn("Draft").build());
        Topic unrelated = topics.save(Topic.builder().titleEn("Other draft").build());
        items.save(TopicItem.builder().topicId(unrelated.getId()).build());

        mvc.perform(get(BASE + "/{id}/readiness", requested.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/{id}/readiness", requested.getId()).with(role(UserRole.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{id}/readiness", requested.getId()).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.violations[*].path").value(org.hamcrest.Matchers.hasItem("topic.items")));
        mvc.perform(get(BASE + "/{id}/readiness", "000000000000000000000000").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("author", "author@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
