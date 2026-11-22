package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleAuthorizationIntegrationTest.AuthorizationController.class)
@Import({SecurityConfig.class, AccessCookieAuthFilter.class, JwtTokenService.class,
        RoleAuthorizationIntegrationTest.AuthorizationFixtureConfiguration.class,
        RoleAuthorizationIntegrationTest.AuthorizationController.class})
@TestPropertySource(properties = {
        "engolearn.security.jwt.secret=role-authorization-test-key-at-least-32-bytes",
        "engolearn.security.jwt.issuer=role-authorization-test"
})
class RoleAuthorizationIntegrationTest {
    private static final String FIXTURE_PATH = "/api/v1/auth/rbac-fixture";
    private static final String ACCOUNT_ID = "68f8f3a9b9e4a12345678901";
    private static final String SESSION_ID = "role-authorization-session";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService tokens;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;

    @Test
    void guestCannotCallProtectedServiceMethod() throws Exception {
        mockMvc.perform(get(FIXTURE_PATH + "/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void userCannotCallContentManagerMethod() throws Exception {
        stubAccount(UserRole.USER, false);

        mockMvc.perform(get(FIXTURE_PATH + "/content-manager").cookie(accessCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(FIXTURE_PATH + "/manager-or-admin").cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void contentManagerCanCallUserMethodAndCannotCallAdminMethod() throws Exception {
        stubAccount(UserRole.CONTENT_MANAGER, false);

        mockMvc.perform(get(FIXTURE_PATH + "/user").cookie(accessCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("user"));
        mockMvc.perform(get(FIXTURE_PATH + "/admin").cookie(accessCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(FIXTURE_PATH + "/manager-or-admin").cookie(accessCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("manager-or-admin"));
    }

    @Test
    void adminCanCallEveryRoleLevelIncludingAnyRoleMethod() throws Exception {
        stubAccount(UserRole.ADMIN, false);
        Cookie cookie = accessCookie();

        mockMvc.perform(get(FIXTURE_PATH + "/user").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("user"));
        mockMvc.perform(get(FIXTURE_PATH + "/content-manager").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("content-manager"));
        mockMvc.perform(get(FIXTURE_PATH + "/admin").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("admin"));
        mockMvc.perform(get(FIXTURE_PATH + "/manager-or-admin").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("manager-or-admin"));
    }

    @Test
    void currentAccountRoleChangeAppliesToTheSameAccessCookie() throws Exception {
        when(users.findById(ACCOUNT_ID)).thenReturn(
                Optional.of(user(UserRole.ADMIN, false)), Optional.of(user(UserRole.USER, false)));
        when(sessions.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session()));
        Cookie cookie = accessCookie();

        mockMvc.perform(get(FIXTURE_PATH + "/admin").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("admin"));
        mockMvc.perform(get(FIXTURE_PATH + "/admin").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedAccountCannotUseValidAccessCookie() throws Exception {
        stubAccount(UserRole.ADMIN, true);

        mockMvc.perform(get(FIXTURE_PATH + "/admin").cookie(accessCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void unlistedHttpPathRemainsPublicForBackwardCompatibility() throws Exception {
        mockMvc.perform(get(FIXTURE_PATH + "/unprotected"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("public"));
    }

    private void stubAccount(UserRole role, boolean locked) {
        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.of(user(role, locked)));
        when(sessions.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session()));
    }

    private User user(UserRole role, boolean locked) {
        return new User(ACCOUNT_ID, "role-test@example.com", "unused", role, locked);
    }

    private RefreshSession session() {
        return RefreshSession.builder().sessionId(SESSION_ID).accountId(ACCOUNT_ID)
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
    }

    private Cookie accessCookie() {
        return new Cookie("ACCESS_TOKEN", tokens.create(ACCOUNT_ID, SESSION_ID, "access", 900));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorizationFixtureConfiguration {
        @Bean AuthorizationFixtureService authorizationFixtureService() {
            return new AuthorizationFixtureService();
        }
    }

    static class AuthorizationFixtureService {
        @PreAuthorize("hasRole('USER')")
        public String user() { return "user"; }

        @PreAuthorize("hasRole('CONTENT_MANAGER')")
        public String contentManager() { return "content-manager"; }

        @PreAuthorize("hasRole('ADMIN')")
        public String admin() { return "admin"; }

        @PreAuthorize("hasAnyRole('CONTENT_MANAGER', 'ADMIN')")
        public String managerOrAdmin() { return "manager-or-admin"; }
    }

    @RestController
    @TestComponent
    @RequestMapping(FIXTURE_PATH)
    static class AuthorizationController {
        private final AuthorizationFixtureService authorization;

        AuthorizationController(AuthorizationFixtureService authorization) {
            this.authorization = authorization;
        }

        @GetMapping("/user")
        ApiResponse<String> user() { return ApiResponse.ok(authorization.user()); }

        @GetMapping("/content-manager")
        ApiResponse<String> contentManager() { return ApiResponse.ok(authorization.contentManager()); }

        @GetMapping("/admin")
        ApiResponse<String> admin() { return ApiResponse.ok(authorization.admin()); }

        @GetMapping("/manager-or-admin")
        ApiResponse<String> managerOrAdmin() { return ApiResponse.ok(authorization.managerOrAdmin()); }

        @GetMapping("/unprotected")
        ApiResponse<String> unprotected() { return ApiResponse.ok("public"); }
    }
}
