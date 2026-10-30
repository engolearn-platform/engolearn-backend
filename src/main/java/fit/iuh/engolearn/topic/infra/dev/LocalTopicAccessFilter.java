package fit.iuh.engolearn.topic.infra.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.InetAddress;

@Component
public class LocalTopicAccessFilter extends OncePerRequestFilter {
    private final boolean localLearnerSelected;

    public LocalTopicAccessFilter(@Value("${engolearn.topic.local-learner-id:}") String selectedId) {
        this.localLearnerSelected = !selectedId.isBlank();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !localLearnerSelected || !request.getRequestURI().startsWith("/api/v1/topic");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
            response.setStatus(403);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":403,\"title\":\"LOCAL_PROFILE_ONLY\",\"code\":\"LOCAL_PROFILE_ONLY\",\"detail\":\"Local learner configuration is not authentication and accepts loopback requests only\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
