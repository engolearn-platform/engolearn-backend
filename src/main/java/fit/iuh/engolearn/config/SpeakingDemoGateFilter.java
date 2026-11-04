package fit.iuh.engolearn.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpeakingDemoGateFilter extends OncePerRequestFilter {
    private final boolean enabled;

    public SpeakingDemoGateFilter(Environment environment,
            @Value("${engolearn.speaking.demo.enabled:false}") boolean configured) {
        this.enabled = configured && Arrays.stream(environment.getActiveProfiles())
                .noneMatch("prod"::equals);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/speaking/") && !path.startsWith("/ws/speaking/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (enabled) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Speaking disabled\","
                + "\"status\":404,\"code\":\"SPEAKING_DISABLED\"}");
    }
}
