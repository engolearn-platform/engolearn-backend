package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PassagePublicationContentTypeExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final ObjectMapper mapper;

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        String prefix = "/api/management/learning-paths/passages/";
        String suffix = "/publish";
        String path = request.getRequestURI();
        if (!(exception instanceof HttpMediaTypeNotSupportedException) || !"POST".equals(request.getMethod())
                || !path.startsWith(prefix) || !path.endsWith(suffix) || path.length() <= prefix.length() + suffix.length()) return null;
        String id = path.substring(prefix.length(), path.length() - suffix.length());
        if (id.contains("/")) return null;
        // Consumes matching fails before a controller is selected, outside controller-scoped advice.
        response.setStatus(415);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        try {
            mapper.writeValue(response.getOutputStream(), Map.of(
                    "type", "about:blank", "title", "Unsupported Media Type", "status", 415,
                    "detail", "Use application/json for passage publication", "code", "UNSUPPORTED_MEDIA_TYPE", "instance", path));
            return new ModelAndView();
        } catch (IOException failure) {
            return null;
        }
    }
}
