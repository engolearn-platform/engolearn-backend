package fit.iuh.engolearn.user.infra.security;

import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.AuthenticatedUserPrincipal;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.repository.UserRepository;
import fit.iuh.engolearn.user.repository.RefreshSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import org.springframework.dao.DataAccessException;

@Component
@RequiredArgsConstructor
public class AccessCookieAuthFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final UserRepository users;
    private final RefreshSessionRepository sessions;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (!"ACCESS_TOKEN".equals(cookie.getName())) continue;
                JwtTokenService.Claims claims;
                try { claims = tokens.verify(cookie.getValue(), "access"); }
                catch (IllegalArgumentException invalidToken) { break; }
                try {
                    RefreshSession session = sessions.findBySessionId(claims.sessionId()).orElse(null);
                    User user = users.findById(claims.accountId()).orElse(null);
                    if (session != null && user != null && !session.isRevoked() && session.getExpiresAt() != null
                            && session.getExpiresAt().isAfter(Instant.now()) && claims.accountId().equals(session.getAccountId())
                            && !user.isLocked()) {
                        UserRole role = user.getRole() == null ? UserRole.USER : user.getRole();
                        var principal = new AuthenticatedUserPrincipal(user.getId(), user.getEmail(), role);
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    }
                } catch (DataAccessException unavailable) {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication service unavailable");
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
