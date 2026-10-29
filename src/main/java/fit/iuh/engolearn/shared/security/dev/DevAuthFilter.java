package fit.iuh.engolearn.shared.security.dev;

import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class DevAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Dev-User-Id";

    private final Map<String, DevUserFixture> fixtures;

    public DevAuthFilter(Map<String, DevUserFixture> fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                && !(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof String
                && "anonymousUser".equals(SecurityContextHolder.getContext().getAuthentication().getPrincipal()))) {
            filterChain.doFilter(request, response);
            return;
        }

        String devUserId = request.getHeader(HEADER_NAME);
        if (devUserId != null && !devUserId.isBlank()) {
            DevUserFixture fixture = fixtures.get(devUserId.trim());
            if (fixture != null) {
                AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                        fixture.getId(), fixture.getEmail(), fixture.getRole());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}

