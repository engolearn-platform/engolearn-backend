package fit.iuh.engolearn.placement.controller;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.when;

@TestConfiguration(proxyBeanMethods = false)
class PlacementCookieAuthSupport {
    @Bean
    FilterRegistrationBean<AccessCookieAuthFilter> accessCookieFilterRegistration(AccessCookieAuthFilter filter) {
        FilterRegistrationBean<AccessCookieAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    static Cookie accessCookie(String userId, UserRole role, UserRepository users,
                               RefreshSessionRepository sessions, JwtTokenService tokens) {
        String sessionId = "placement-test-session-" + userId;
        when(users.findById(userId)).thenReturn(Optional.of(
                new User(userId, userId + "@example.test", "unused", role, false)));
        when(sessions.findBySessionId(sessionId)).thenReturn(Optional.of(RefreshSession.builder()
                .sessionId(sessionId).accountId(userId)
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build()));
        return new Cookie("ACCESS_TOKEN", tokens.create(userId, sessionId, "access", 900));
    }
}
