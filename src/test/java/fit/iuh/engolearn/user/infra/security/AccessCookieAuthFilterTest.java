package fit.iuh.engolearn.user.infra.security;

import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.repository.UserRepository;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccessCookieAuthFilterTest {
    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void sessionStoreFailureReturns503InsteadOfAnonymousAuthentication() throws Exception {
        JwtTokenService tokens = mock(JwtTokenService.class);
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        AccessCookieAuthFilter filter = new AccessCookieAuthFilter(tokens, users, sessions);
        when(tokens.verify("access", "access")).thenReturn(new JwtTokenService.Claims("account", "session", "access"));
        when(sessions.findBySessionId("session")).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", "access"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(users);
        verify(chain, never()).doFilter(any(), any());
    }
}
