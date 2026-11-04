package fit.iuh.engolearn.config;

import fit.iuh.engolearn.learningpath.controller.LearningPathController;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
import fit.iuh.engolearn.learningpath.service.LearningPathRecommendationService;
import fit.iuh.engolearn.learningpath.service.LearningPathCreationService;
import fit.iuh.engolearn.learningpath.service.LearningPathDayPracticeService;
import fit.iuh.engolearn.learningpath.service.LearningPathAnswerDraftService;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.topic.controller.TopicBrowseController;
import fit.iuh.engolearn.topic.controller.TopicLearningController;
import fit.iuh.engolearn.topic.service.TopicBrowseService;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import fit.iuh.engolearn.user.controller.AuthController;
import fit.iuh.engolearn.user.service.UserAuthService;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import jakarta.servlet.http.Cookie;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@TestPropertySource(properties = "engolearn.speaking.demo.enabled=true")
class SecurityIntegrationTest {

    private static final String USER_ID = "68f8f3a9b9e4a12345678901";

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
    @MockitoBean private fit.iuh.engolearn.learningpath.service.LearningPathDayResultService dayResultService;
    @MockitoBean private UserAuthService userAuthService;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private fit.iuh.engolearn.user.repository.UserRepository userRepository;
    @MockitoBean private fit.iuh.engolearn.user.repository.RefreshSessionRepository refreshSessionRepository;

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
    void authMutationsDoNotRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void csrfEndpointIsRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void topicBrowseEndpointsStayPublicForAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/t1"))
                .andExpect(status().isOk());
    }

    @Test
    void validAccessCookieAuthenticatesTheCurrentUser() throws Exception {
        stubAccessCookie(UserRole.USER);
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ACCESS_TOKEN", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("learner@engolearn.local"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void devUserHeaderCannotAuthenticateTheCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", "dev-admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousSpeakingRequestRequiresTheSharedSession() throws Exception {
        mockMvc.perform(get("/api/v1/speaking/sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void aClientSuppliedRoleHeaderIsIgnored() throws Exception {
        stubAccessCookie(UserRole.USER);
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("ACCESS_TOKEN", "valid-token"))
                        .header("X-Dev-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    private void stubAccessCookie(UserRole role) {
        when(jwtTokenService.verify("valid-token", "access"))
                .thenReturn(new JwtTokenService.Claims(USER_ID, "session-1", "access"));
        when(refreshSessionRepository.findBySessionId("session-1")).thenReturn(Optional.of(
                RefreshSession.builder().sessionId("session-1").accountId(USER_ID)
                        .expiresAt(Instant.now().plusSeconds(3600)).build()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(
                new User(USER_ID, "learner@engolearn.local", "unused", role, false)));
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

