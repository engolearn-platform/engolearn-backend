package fit.iuh.engolearn.config;

import fit.iuh.engolearn.user.infra.security.AccessCookieAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${engolearn.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Environment environment,
                                                   AccessCookieAuthFilter accessCookieAuthFilter,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/subscription/admin", "/api/v1/subscription/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/subscription/me", "/api/v1/subscription/me/**", "/api/v1/quota/me/**").authenticated()
                        .requestMatchers("/api/learning-paths", "/api/learning-paths/**").authenticated()
                        .requestMatchers("/api/v1/topics/mine").hasAnyRole("USER", "CONTENT_MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics", "/api/v1/topics/{topicId}").permitAll()
                        .requestMatchers("/api/v1/topics", "/api/v1/topics/**", "/api/v1/topic-items", "/api/v1/topic-items/**",
                                "/api/v1/topic-attempts", "/api/v1/topic-attempts/**", "/api/v1/topic-retry-sessions", "/api/v1/topic-retry-sessions/**",
                                "/api/v1/topic-learning", "/api/v1/topic-learning/**").hasAnyRole("USER", "CONTENT_MANAGER", "ADMIN")
                        .anyRequest().permitAll()
                );

        http.addFilterBefore(accessCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) ->
                writeProblemDetail(response, objectMapper, HttpStatus.UNAUTHORIZED, authException, request.getRequestURI());
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) ->
                writeProblemDetail(response, objectMapper, HttpStatus.FORBIDDEN, accessDeniedException, request.getRequestURI());
    }

    private void writeProblemDetail(HttpServletResponse response, ObjectMapper objectMapper,
                                    HttpStatus status, Exception exception, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                exception.getMessage() == null ? status.getReasonPhrase() : exception.getMessage());
        problemDetail.setType(URI.create("about:blank"));
        if (path.startsWith("/api/v1/subscription/") || path.startsWith("/api/v1/quota/")
                || path.startsWith("/api/v1/auth/") || path.equals("/api/learning-paths")
                || path.startsWith("/api/learning-paths/")) {
            problemDetail.setDetail(status == HttpStatus.UNAUTHORIZED ? "Authentication is required" : "Access is denied");
            problemDetail.setInstance(URI.create(path));
            problemDetail.setProperty("code", status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED");
            response.setHeader("Cache-Control", "no-store");
        }
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
