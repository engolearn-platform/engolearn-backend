package fit.iuh.engolearn.topic.support;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import fit.iuh.engolearn.user.repository.UserProfileRepository;
import fit.iuh.engolearn.user.repository.UserRepository;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

public final class TopicTestAuthentication {
    public static final String USER_ID = "user-mock-001";

    private TopicTestAuthentication() {}

    public static RequestPostProcessor user() {
        var principal = new AuthenticatedUserPrincipal("user-mock-001", "test@example.test", UserRole.USER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    public static RequestPostProcessor authentication(Authentication authentication) {
        return request -> {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            new RequestAttributeSecurityContextRepository().saveContext(context, request, new MockHttpServletResponse());
            return request;
        };
    }

    public static String persistedAccessToken(UserRepository users, UserProfileRepository profiles,
                                              RefreshSessionRepository sessions, JwtTokenService tokens) {
        String sessionId = "topic-test-session-" + USER_ID;
        users.save(new User(USER_ID, USER_ID + "@example.test", "unused", UserRole.USER, false));
        profiles.save(UserProfile.builder().id(USER_ID).cefrLevel(CefrLevel.A1).build());
        sessions.save(RefreshSession.builder()
                .id(sessionId)
                .sessionId(sessionId)
                .accountId(USER_ID)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());
        return tokens.create(USER_ID, sessionId, "access", 900);
    }
}
