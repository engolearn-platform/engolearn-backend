package fit.iuh.engolearn.config;

import fit.iuh.engolearn.learningpath.controller.LearningPathController;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        TopicBrowseController.class,
        TopicLearningController.class,
        LearningPathController.class,
        AuthController.class
})
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class})
@TestPropertySource(properties = "engolearn.security.dev-auth.enabled=true")
class SecurityIntegrationTest {

    private static final String DEV_USER = "dev-user";
    private static final String DEV_CM = "dev-cm";
    private static final String DEV_ADMIN = "dev-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @MockitoBean
    private TopicBrowseService topicBrowseService;

    @MockitoBean
    private TopicLearningService topicLearningService;

    @MockitoBean
    private LearningPathService learningPathService;
    @MockitoBean private fit.iuh.engolearn.topic.service.TopicLearnerCatalogService learnerCatalogService;
    @MockitoBean private fit.iuh.engolearn.topic.service.TopicAttemptService attemptService;

    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathRecommendationService recommendationService;
    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathCreationService creationService;
    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathDayPracticeService dayPracticeService;
    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathAnswerDraftService answerDraftService;
    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathDaySubmissionService submissionService;

    @Test
    void legacyFeatureEndpointsStayUnprotectedUntilMigration() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/i1/content"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/topics/t1/progress"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/learning-paths")
                        .param("userId", "68f8f3a9b9e4a12345678901"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/learning-paths/68f8f3a9b9e4a12345678901")
                        .param("userId", "68f8f3a9b9e4a12345678901"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousRequestToTheCurrentUserEndpointIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void anonymousRequestToTheCurrentUserEndpointIsUnauthorizedWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void topicBrowseEndpointsStayPublicForAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/t1"))
                .andExpect(status().isOk());
    }

    @Test
    void eachDevFixtureUserIsAuthenticatedOnTheCurrentUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", DEV_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("68f8f3a9b9e4a12345678901"))
                .andExpect(jsonPath("$.data.email").value("dev.user@engolearn.local"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", DEV_CM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("68f8f3a9b9e4a12345678902"))
                .andExpect(jsonPath("$.data.email").value("dev.cm@engolearn.local"))
                .andExpect(jsonPath("$.data.role").value("CONTENT_MANAGER"));

        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", DEV_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("68f8f3a9b9e4a12345678903"))
                .andExpect(jsonPath("$.data.email").value("dev.admin@engolearn.local"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void anUnknownDevUserHeaderLeavesTheRequestAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", "not-a-fixture"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aClientSuppliedRoleHeaderIsIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Dev-User-Id", DEV_USER)
                        .header("X-Dev-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void accessDeniedIsReportedAsAForbiddenProblemDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("Access is denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":403");
    }

    @Test
    void corsPreflightIsAllowedForTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/topics")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}

