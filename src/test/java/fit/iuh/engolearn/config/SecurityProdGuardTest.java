package fit.iuh.engolearn.config;

import fit.iuh.engolearn.learningpath.controller.LearningPathController;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
import fit.iuh.engolearn.shared.security.dev.DevAuthFilter;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.topic.controller.TopicBrowseController;
import fit.iuh.engolearn.topic.controller.TopicLearningController;
import fit.iuh.engolearn.topic.service.TopicBrowseService;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import fit.iuh.engolearn.user.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        TopicBrowseController.class,
        TopicLearningController.class,
        LearningPathController.class,
        AuthController.class
})
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class})
@ActiveProfiles("prod")
class SecurityProdGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private TopicBrowseService topicBrowseService;

    @MockitoBean
    private TopicLearningService topicLearningService;

    @MockitoBean
    private LearningPathService learningPathService;

    @Test
    void theDevAuthFilterIsNotRegisteredInProd() {
        boolean devAuthFilterPresent = springSecurityFilterChain.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .anyMatch(DevAuthFilter.class::isInstance);

        assertThat(devAuthFilterPresent).isFalse();
    }

    @Test
    void theDevHeaderIsIgnoredWhenTheProdProfileIsActive() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", "dev-admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicTopicBrowseStillWorksInProd() throws Exception {
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk());
    }
}

