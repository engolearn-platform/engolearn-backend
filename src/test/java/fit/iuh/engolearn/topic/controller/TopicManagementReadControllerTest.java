package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.topic.mapper.TopicManagementItemMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicManagementQueries;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.impl.TopicManagementReadServiceImpl;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TopicManagementReadController.class)
@Import({SecurityConfig.class, TopicManagementReadServiceImpl.class, TopicManagementItemMapper.class})
class TopicManagementReadControllerTest {
    private static final String BASE = "/api/management/topics";

    @Autowired private MockMvc mvc;
    @Autowired private TopicManagementReadService service;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;
    @MockitoBean private TopicRepository topics;
    @MockitoBean private TopicItemRepository items;
    @MockitoBean private TopicVersionRepository releases;
    @MockitoBean private TopicItemVersionRepository versions;
    @MockitoBean private TopicManagementQueries queries;

    @Test
    void rejectsUnauthenticatedAndLearnerRequestsBeforeDatabaseAccess() throws Exception {
        for (String path : List.of(BASE, BASE + "/t", BASE + "/t/items", BASE + "/t/items/i")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(head(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(role(UserRole.USER))).andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> service.items("t")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(topics, items, releases, versions, queries);
    }

    @Test
    void returnsAnEmptyListAndRejectsInvalidFilters() throws Exception {
        when(queries.findPage(null, null, null, null, 0, 20)).thenReturn(Page.empty());
        mvc.perform(get(BASE).with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.counters.total").value(0));
        mvc.perform(get(BASE).param("size", "101").with(role(UserRole.CONTENT_MANAGER)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(BASE + "/absent").with(role(UserRole.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void selectsAnExplicitDraftOverTheReleasedItemWithoutUsingBetaContent() throws Exception {
        var item = TopicItem.builder().id("i").topicId("t").build();
        var published = version("v1", 1, ContentStatus.PUBLISHED);
        var draft = version("v2", 2, ContentStatus.DRAFT);
        draft.setContext(TopicContextVo.builder().prompt("Saved draft prompt").build());
        when(topics.findById("t")).thenReturn(Optional.of(Topic.builder().id("t").latestPublishedVersion("r1").build()));
        when(releases.findById("r1")).thenReturn(Optional.of(TopicVersion.builder().id("r1").topicId("t")
                .itemVersionIds(List.of("v1")).status(ContentStatus.PUBLISHED).build()));
        when(items.findByIdAndTopicId("i", "t")).thenReturn(Optional.of(item));
        when(versions.findManagementMetadataForItem("t", "i"))
                .thenReturn(List.of(version("v3", 3, ContentStatus.BETA), published, draft));
        when(versions.findByIdAndTopicIdAndTopicItemId("v2", "t", "i")).thenReturn(Optional.of(draft));

        mvc.perform(get(BASE + "/t/items/i").with(role(UserRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("i"))
                .andExpect(jsonPath("$.data.versionId").value("v2"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.context.prompt").value("Saved draft prompt"));
    }

    private static TopicItemVersion version(String id, int number, ContentStatus status) {
        return TopicItemVersion.builder().id(id).topicId("t").topicItemId("i").version(number).status(status).build();
    }

    private static RequestPostProcessor role(UserRole role) {
        var principal = new AuthenticatedUserPrincipal("reader", "reader@example.test", role);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
