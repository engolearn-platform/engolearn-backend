package fit.iuh.engolearn.config;

import fit.iuh.engolearn.learningpath.controller.LearningPathController;
import fit.iuh.engolearn.learningpath.service.LearningPathService;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.dto.request.SaveLearningPathAnswerDraftRequest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.shared.security.impl.SecurityContextCurrentUserProvider;
import fit.iuh.engolearn.topic.controller.TopicBrowseController;
import fit.iuh.engolearn.topic.controller.TopicLearningController;
import fit.iuh.engolearn.topic.service.TopicBrowseService;
import fit.iuh.engolearn.topic.service.TopicLearningService;
import fit.iuh.engolearn.user.controller.AuthController;
import fit.iuh.engolearn.user.service.UserAuthService;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
@Import({SecurityConfig.class, SecurityContextCurrentUserProvider.class,
        AccessCookieAuthFilter.class, JwtTokenService.class, SecurityIntegrationTest.FilterConfig.class})
@TestPropertySource(properties = {
        "engolearn.security.jwt.secret=security-integration-test-key-at-least-32-bytes",
        "engolearn.security.jwt.issuer=security-integration-test",
        "engolearn.cors.allowed-origins=http://localhost:3000"
})
class SecurityIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FilterConfig {
        @Bean
        FilterRegistrationBean<AccessCookieAuthFilter> accessCookieFilterRegistration(AccessCookieAuthFilter filter) {
            // Run authentication inside SecurityFilterChain, after its security context has been initialized.
            FilterRegistrationBean<AccessCookieAuthFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    private static final String USER_ID = "68f8f3a9b9e4a12345678901";
    private static final String OTHER_USER_ID = "68f8f3a9b9e4a12345678902";
    private static final String PATH_ID = "68f8f3a9b9e4a12345678903";
    private static final String ATTEMPT_ID = "68f8f3a9b9e4a12345678904";
    private static final String SESSION_ID = "security-integration-session";
    private static final String DAY_URL = "/api/learning-paths/" + PATH_ID + "/weeks/1/days/1";
    private static final String CREATE_BODY = """
            {"attemptId":"%s","microSkills":["Reading for Gist"]}
            """.formatted(ATTEMPT_ID);

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
    @Autowired private JwtTokenService jwtTokenService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private RefreshSessionRepository refreshSessionRepository;

    @Test
    void topicLearningEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/topic-items/i1/content"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/topics/t1/progress"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("learningPathRequests")
    void learningPathEndpointsRequireAuthentication(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value(path.split("\\?", 2)[0]))
                .andExpect(header().string("Cache-Control", "no-store"));

        verifyNoLearningPathCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/assessment/preparation", "/api/v1/assessment/attempts"})
    void assessmentEndpointsRequireAuthenticationWithProblemDetail(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(header().string("Cache-Control", "no-store"));
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

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void accessCookieAuthenticatesThePersistedUserRole(UserRole role) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("student@example.com"))
                .andExpect(jsonPath("$.data.role").value(role.name()))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev-user", "dev-cm", "dev-admin", "not-a-fixture"})
    void legacyDevHeadersCannotAuthenticateRequests(String identity) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Dev-User-Id", identity))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learning-paths").header("X-Dev-User-Id", identity))
                .andExpect(status().isUnauthorized());
        verifyNoLearningPathCalls();
    }

    @Test
    void aClientSuppliedRoleHeaderIsIgnored() throws Exception {
        Cookie cookie = accessCookie(UserRole.USER);
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(cookie)
                        .header("X-Dev-User-Id", "dev-admin")
                        .header("X-Dev-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/api/v1/subscription/admin")
                        .cookie(cookie)
                        .header("X-Dev-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @MethodSource("learningPathRequests")
    void learningPathEndpointsUseCookieIdentityDespiteClientSuppliedUserId(
            HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(request(method, path)
                        .cookie(accessCookie(UserRole.USER))
                        .param("userId", OTHER_USER_ID)
                        .header("X-Dev-User-Id", "dev-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        String endpoint = path.split("\\?", 2)[0];
        if (endpoint.equals("/api/learning-paths/recommendation")) {
            verify(recommendationService).recommend(USER_ID, ATTEMPT_ID);
        } else if (endpoint.equals("/api/learning-paths/recommendation/by-assessment")) {
            verify(recommendationService).recommendForAssessment(USER_ID, "native-assessment-id");
        } else if (endpoint.equals("/api/learning-paths/search")) {
            verify(learningPathService).search(USER_ID, "Gist");
        } else if (path.equals("/api/learning-paths") && method == HttpMethod.POST) {
            verify(creationService).create(new CreateLearningPathRequest(
                    ATTEMPT_ID, List.of("Reading for Gist")), USER_ID);
        } else if (path.equals("/api/learning-paths")) {
            verify(learningPathService).getByUserId(USER_ID,
                    fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus.ACTIVE);
        } else if (path.endsWith("/start")) {
            verify(dayPracticeService).start(PATH_ID, 1, 1, USER_ID);
        } else if (path.endsWith("/draft")) {
            verify(answerDraftService).save(PATH_ID, 1, 1, "q-1", USER_ID,
                    new SaveLearningPathAnswerDraftRequest("A"));
        } else if (path.endsWith("/submit")) {
            verify(submissionService).submit(PATH_ID, 1, 1, USER_ID);
        } else if (path.endsWith("/result")) {
            verify(dayResultService).getResult(PATH_ID, 1, 1, USER_ID);
        } else {
            verify(learningPathService).getDetail(PATH_ID, USER_ID);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "refresh", "wrong-issuer", "tampered"})
    void invalidAccessTokensCannotReachLearningPath(String failure) throws Exception {
        String token = switch (failure) {
            case "expired" -> jwtTokenService.create(USER_ID, SESSION_ID, "access", -60);
            case "refresh" -> jwtTokenService.create(USER_ID, SESSION_ID, "refresh", 900);
            case "wrong-issuer" -> new JwtTokenService(
                    "security-integration-test-key-at-least-32-bytes", "another-issuer")
                    .create(USER_ID, SESSION_ID, "access", 900);
            case "tampered" -> jwtTokenService.create(USER_ID, SESSION_ID, "access", 900) + "invalid";
            default -> "invalid-token";
        };
        mockMvc.perform(get("/api/learning-paths").cookie(new Cookie("ACCESS_TOKEN", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(userRepository, refreshSessionRepository);
        verifyNoLearningPathCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-session", "revoked", "expired", "wrong-account", "locked", "missing-user"})
    void unusableSessionsCannotReachLearningPath(String failure) throws Exception {
        User user = user(UserRole.USER);
        RefreshSession session = session();
        switch (failure) {
            case "revoked" -> session.setRevoked(true);
            case "expired" -> session.setExpiresAt(Instant.now().minusSeconds(60));
            case "wrong-account" -> session.setAccountId(OTHER_USER_ID);
            case "locked" -> user.setLocked(true);
            default -> { }
        }
        when(userRepository.findById(USER_ID)).thenReturn(
                failure.equals("missing-user") ? Optional.empty() : Optional.of(user));
        when(refreshSessionRepository.findBySessionId(SESSION_ID)).thenReturn(
                failure.equals("missing-session") ? Optional.empty() : Optional.of(session));

        mockMvc.perform(get("/api/learning-paths").cookie(tokenCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoLearningPathCalls();
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

    private Cookie accessCookie(UserRole role) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(role)));
        when(refreshSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session()));
        return tokenCookie();
    }

    private Cookie tokenCookie() {
        return new Cookie("ACCESS_TOKEN", jwtTokenService.create(USER_ID, SESSION_ID, "access", 900));
    }

    private User user(UserRole role) {
        return new User(USER_ID, "student@example.com", "unused", role, false);
    }

    private RefreshSession session() {
        return RefreshSession.builder().sessionId(SESSION_ID).accountId(USER_ID)
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
    }

    private void verifyNoLearningPathCalls() {
        verifyNoInteractions(learningPathService, recommendationService, creationService,
                dayPracticeService, answerDraftService, submissionService, dayResultService);
    }

    private static Stream<Arguments> learningPathRequests() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/learning-paths", ""),
                Arguments.of(HttpMethod.GET, "/api/learning-paths/search?keyword=Gist", ""),
                Arguments.of(HttpMethod.GET, "/api/learning-paths/recommendation?attemptId=" + ATTEMPT_ID, ""),
                Arguments.of(HttpMethod.GET, "/api/learning-paths/recommendation/by-assessment?assessmentAttemptId=native-assessment-id", ""),
                Arguments.of(HttpMethod.POST, "/api/learning-paths", CREATE_BODY),
                Arguments.of(HttpMethod.GET, "/api/learning-paths/" + PATH_ID, ""),
                Arguments.of(HttpMethod.POST, DAY_URL + "/start", ""),
                Arguments.of(HttpMethod.PUT, DAY_URL + "/questions/q-1/draft", "{\"selectedOptionId\":\"A\"}"),
                Arguments.of(HttpMethod.POST, DAY_URL + "/submit", ""),
                Arguments.of(HttpMethod.GET, DAY_URL + "/result", "")
        );
    }
}

