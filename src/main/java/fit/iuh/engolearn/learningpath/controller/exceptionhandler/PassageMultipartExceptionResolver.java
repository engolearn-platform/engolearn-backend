package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PassageMultipartExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final ObjectMapper mapper;

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        // Multipart parsing can fail before MVC selects a controller, so scoped advice cannot handle it.
        if (!(exception instanceof MaxUploadSizeExceededException)
                || !"/api/management/learning-paths/passages".equals(request.getRequestURI())
                || !"POST".equals(request.getMethod())) {
            return null;
        }
        response.setStatus(413);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        try {
            mapper.writeValue(response.getOutputStream(), Map.of(
                    "type", "about:blank", "title", "Payload Too Large", "status", 413,
                    "detail", "Upload exceeds the configured size limit",
                    "code", "UPLOAD_TOO_LARGE", "instance", request.getRequestURI()));
            return new ModelAndView();
        } catch (IOException failure) {
            return null;
        }
    }
}
