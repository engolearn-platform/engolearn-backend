package fit.iuh.engolearn.topic.support;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TopicTestAuthentication {
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
}
