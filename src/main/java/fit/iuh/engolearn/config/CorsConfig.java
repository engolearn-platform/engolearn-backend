package fit.iuh.engolearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Central CORS configuration for the EngoLearn backend.
 *
 * <p>The frontend development server runs on a different origin (Vite defaults to
 * http://localhost:3000), so browsers require explicit CORS allow-listing for
 * cross-origin requests and preflight (OPTIONS) checks. Allowed origins are
 * config-driven via {@code engolearn.cors.allowed-origins} so that staging and
 * production environments can override them without code changes. Only
 * {@code /api/**} endpoints are exposed to cross-origin requests; no controller-level
 * {@code @CrossOrigin} annotations are used.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${engolearn.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        List<String> origins;
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            origins = List.of("http://localhost:3000");
        } else {
            origins = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .collect(Collectors.toList());
            if (origins.isEmpty()) {
                origins = List.of("http://localhost:3000");
            }
        }
        this.allowedOrigins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
