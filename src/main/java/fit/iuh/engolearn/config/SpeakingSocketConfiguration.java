package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.controller.SpeakingSocketHandler;
import fit.iuh.engolearn.speaking.controller.SpeakingSocketHandshake;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class SpeakingSocketConfiguration implements WebSocketConfigurer {
    private final SpeakingSocketHandler handler;
    private final SpeakingSocketHandshake handshake;
    private final List<String> origins;

    public SpeakingSocketConfiguration(SpeakingSocketHandler handler, SpeakingRuntime runtime, ObjectMapper mapper,
                                       @Value("${engolearn.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.handler = handler;
        this.origins = Arrays.stream(allowedOrigins.split(",")).map(String::strip)
                .filter(origin -> !origin.isEmpty() && !origin.contains("*")).toList();
        this.handshake = new SpeakingSocketHandshake(runtime, mapper, origins);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/speaking/{sessionId}")
                .addInterceptors(handshake).setAllowedOrigins(origins.toArray(String[]::new));
    }
}
