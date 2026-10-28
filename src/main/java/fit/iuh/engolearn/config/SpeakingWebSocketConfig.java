package fit.iuh.engolearn.config;

import fit.iuh.engolearn.speaking.controller.SpeakingSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
@EnableScheduling
public class SpeakingWebSocketConfig implements WebSocketConfigurer {
    private final SpeakingSocketHandler handler;
    private final String allowedOrigins;

    public SpeakingWebSocketConfig(SpeakingSocketHandler handler,
            @Value("${engolearn.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
        registry.addHandler(handler, "/ws/speaking/*").setAllowedOrigins(origins);
    }
}
