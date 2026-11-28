package fit.iuh.engolearn.user.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

class AccessCookieAuthFilterAsyncTest {
    @Test void verifiedCookieAuthenticationSurvivesSameRequestAsyncRedispatch() throws Exception {
        String accountId = "account-1";
        String sessionId = "session-1";
        long sessionEpoch = 7L;
        JwtTokenService tokens = new JwtTokenService("async-test-secret-at-least-32-bytes", "test-issuer");
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        RefreshSessionRepository sessions = org.mockito.Mockito.mock(RefreshSessionRepository.class);
        when(sessions.findBySessionId(sessionId)).thenReturn(Optional.of(RefreshSession.builder()
                .sessionId(sessionId).accountId(accountId).expiresAt(Instant.now().plusSeconds(300)).revoked(false).accountSessionEpoch(sessionEpoch).build()));
        User learner = new User(accountId, "learner@example.test", "unused", UserRole.USER, false);
        learner.setSessionEpoch(sessionEpoch);
        when(users.findById(accountId)).thenReturn(Optional.of(learner));
        AccessCookieAuthFilter filter = new AccessCookieAuthFilter(tokens, users, sessions,
                new RequestAttributeSecurityContextRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companion/conversations/1/turns");
        request.setCookies(new Cookie("ACCESS_TOKEN", tokens.create(accountId, sessionId, "access", 300)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        SecurityContextHolder.clearContext();

        request.setDispatcherType(DispatcherType.ASYNC);
        SecurityContextRepository repository = new RequestAttributeSecurityContextRepository();
        var restored = repository.loadDeferredContext(request).get();
        var principal = (AuthenticatedUserPrincipal) restored.getAuthentication().getPrincipal();
        assertEquals(accountId, principal.getId());
        assertEquals(sessionId, principal.getSessionId());
        assertEquals(sessionEpoch, principal.getAccountSessionEpoch());
        assertEquals(true, restored.getAuthentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }
}
