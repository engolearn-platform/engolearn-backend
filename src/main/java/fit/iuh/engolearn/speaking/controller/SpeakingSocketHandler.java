package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.speaking.service.SpeakingRealtimeService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;

@Component
public class SpeakingSocketHandler extends TextWebSocketHandler {
    private final SpeakingRealtimeService realtime;
    private final ObjectMapper mapper;

    public SpeakingSocketHandler(SpeakingRealtimeService realtime, ObjectMapper mapper) {
        this.realtime = realtime;
        this.mapper = mapper;
    }

    @Override public void afterConnectionEstablished(WebSocketSession socket) {
        URI uri = socket.getUri();
        if (uri == null) { close(socket, CloseStatus.POLICY_VIOLATION); return; }
        String path = uri.getPath();
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        String ticket = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
        if (ticket == null) { close(socket, CloseStatus.POLICY_VIOLATION); return; }
        realtime.open(socket.getId(), sessionId, ticket, event -> send(socket, event))
                .exceptionally(error -> { close(socket, CloseStatus.POLICY_VIOLATION); return null; });
    }

    @Override protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        try {
            JsonNode data = mapper.readTree(message.getPayload());
            String type = data.path("type").asText("");
            String turnId = data.path("turnId").asText("");
            long epoch = data.path("controllerEpoch").asLong(-1);
            if ("START_TURN".equals(type)) realtime.start(socket.getId(), turnId, epoch);
            else if ("END_OF_TURN".equals(type)) realtime.end(socket.getId(), turnId, epoch);
            else if ("CANCEL_TURN".equals(type)) realtime.cancel(socket.getId(), turnId, epoch);
            else send(socket, Map.of("type", "ERROR", "message", "Unknown socket message"));
        } catch (Exception error) {
            send(socket, Map.of("type", "ERROR", "message", error.getMessage() == null
                    ? "Speaking request failed" : error.getMessage()));
        }
    }

    @Override protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) {
        try {
            byte[] pcm = new byte[message.getPayloadLength()];
            message.getPayload().get(pcm);
            realtime.pcm(socket.getId(), pcm);
        } catch (Exception error) {
            send(socket, Map.of("type", "ERROR", "message", error.getMessage() == null
                    ? "Audio frame failed" : error.getMessage()));
        }
    }

    @Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        realtime.close(socket.getId());
    }

    private void send(WebSocketSession socket, Map<String, Object> event) {
        try {
            synchronized (socket) {
                if (socket.isOpen()) socket.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
            }
        } catch (Exception ignored) {
            close(socket, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(WebSocketSession socket, CloseStatus status) {
        try { if (socket.isOpen()) socket.close(status); } catch (Exception ignored) { }
    }
}
