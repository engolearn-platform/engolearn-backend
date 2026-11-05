package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.config.SecurityConfig;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.service.*;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static fit.iuh.engolearn.topic.support.TopicTestAuthentication.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TopicBrowseController.class, TopicLearningController.class, TopicAttemptController.class,
        TopicRetryController.class, TopicResetController.class, TopicViewerController.class})
@Import({SecurityConfig.class, JwtTokenService.class})
class TopicAuthenticationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @MockitoBean private TopicLearnerCatalogService catalog;
    @MockitoBean private TopicLearningService learning;
    @MockitoBean private TopicAttemptService attempts;
    @MockitoBean private TopicRetryService retries;
    @MockitoBean private TopicResetService resets;
    @MockitoBean private TopicViewerService viewers;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;
    @Autowired private org.springframework.web.context.WebApplicationContext context;
    @Autowired private org.springframework.security.web.FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void useOnlyTheConfiguredSecurityChain() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain).build();
    }

    @Test
    void onlyCatalogAndDetailArePublicAndPassNoActor() throws Exception {
        mvc.perform(get("/api/v1/topics")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics/t1")).andExpect(status().isOk());
        verify(catalog).discover(isNull(), any(GetAvailableTopicRequest.class));
        verify(catalog).detail(null, "t1");
    }

    @Test
    void anonymousReadsAndWritesAcrossAllTopicNamespacesAreRejected() throws Exception {
        for (String path : List.of("/topics/mine", "/topics/t1/progress", "/topics/t1/result",
                "/topic-items/i1/content", "/topic-items/i1/position", "/topic-attempts/a1",
                "/topic-retry-sessions/r1", "/topic-learning/viewer")) {
            mvc.perform(get("/api/v1" + path)).andExpect(status().isUnauthorized());
        }
        for (String path : List.of("/topics/t1/reset", "/topic-items/i1/attempts", "/topic-attempts/a1/advance",
                "/topics/t1/retry-sessions", "/topic-learning/local-profiles", "/topics")) {
            mvc.perform(post("/api/v1" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/v1/topic-items/i1/position").contentType("application/json")
                .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(catalog, learning, attempts, retries, resets, viewers);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void everyAllowedRoleUsesTheCookiePrincipalId(UserRole role) throws Exception {
        Cookie cookie = cookie("account-" + role, role);
        String actor = "account-" + role;
        mvc.perform(get("/api/v1/topics/mine").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topic-learning/viewer").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topic-attempts/a1/content").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topic-retry-sessions/r1").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics/t1/progress").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/topics/t1/reset").cookie(cookie).header("Idempotency-Key", "reset-1")
                .contentType("application/json").content("{\"generation\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/topics/t1").cookie(cookie)).andExpect(status().isOk());
        verify(catalog).mine(actor, "IN_PROGRESS", null, null);
        verify(catalog).discover(eq(actor), any());
        verify(catalog).detail(actor, "t1");
        verify(viewers).getViewer(actor);
        verify(attempts).content(actor, "a1");
        verify(attempts).topicProgress(actor, "t1");
        verify(retries).getSession(actor, "r1");
        verify(resets).reset(eq(actor), eq("t1"), any(), eq("reset-1"));
    }

    @Test
    void unrelatedAuthorityCannotInvokeTopicOperations() throws Exception {
        var principal = new AuthenticatedUserPrincipal("account-a", "a@example.test", UserRole.USER);
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR")));
        mvc.perform(get("/api/v1/topics/mine").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/topics/t1/reset").with(authentication(auth)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(catalog, resets);
    }

    @Test
    void invalidOrExpiredCookiesAndClientIdentityHeadersCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/topics/mine").cookie(new Cookie("ACCESS_TOKEN", "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/topics/mine").cookie(new Cookie("ACCESS_TOKEN",
                tokens.create("account-a", "session-a", "access", -1))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/topics/mine").header("X-Dev-User-Id", "account-a")
                .header("X-Dev-User-Role", "ADMIN")).andExpect(status().isUnauthorized());
        verifyNoInteractions(catalog);
    }

    @Test
    void authenticatedRetiredEndpointsKeepGoneSemantics() throws Exception {
        Cookie cookie = cookie("account-a", UserRole.USER);
        mvc.perform(get("/api/v1/topic-items/i1/content").cookie(cookie)).andExpect(status().isGone());
        mvc.perform(put("/api/v1/topic-items/i1/position").cookie(cookie).contentType("application/json")
                .content("{\"stage\":\"CONTEXT\",\"itemIndex\":0}"))
                .andExpect(status().isGone());
        verifyNoInteractions(learning, attempts);
    }

    private Cookie cookie(String actor, UserRole role) {
        String sessionId = "session-" + actor;
        when(users.findById(actor)).thenReturn(Optional.of(new User(actor, actor + "@example.test", "unused", role, false)));
        when(sessions.findBySessionId(sessionId)).thenReturn(Optional.of(RefreshSession.builder()
                .sessionId(sessionId).accountId(actor).expiresAt(Instant.now().plusSeconds(300)).revoked(false).build()));
        return new Cookie("ACCESS_TOKEN", tokens.create(actor, sessionId, "access", 300));
    }
}
