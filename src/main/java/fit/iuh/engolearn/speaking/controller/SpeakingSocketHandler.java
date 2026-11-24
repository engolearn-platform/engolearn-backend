package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.service.SpeakingRealtime;
import fit.iuh.engolearn.speaking.service.SpeakingRealtime.*;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime.Lease;
import fit.iuh.engolearn.speaking.utils.SpeakingFrames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpeakingSocketHandler extends AbstractWebSocketHandler {
    public static final String LEASE = SpeakingSocketHandler.class.getName() + ".lease";
    private static final String CONNECTION = SpeakingSocketHandler.class.getName() + ".connection";
    private final SpeakingRealtime realtime;
    private final ObjectMapper mapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) throws IOException {
        socket.setTextMessageSizeLimit(SpeakingFrames.MAX_TEXT + 1);
        socket.setBinaryMessageSizeLimit(SpeakingFrames.HEADER + SpeakingFrames.CHUNK + 1);
        if (socket instanceof NativeWebSocketSession unwrappable) {
            var nativeSession = unwrappable.getNativeSession(jakarta.websocket.Session.class);
            if (nativeSession != null) {
                nativeSession.getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 3000L);
            }
        }
        Lease lease = (Lease) socket.getAttributes().get(LEASE);
        if (lease == null) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            socket.getAttributes().put(CONNECTION, realtime.connect(lease, new Channel() {
                @Override public void send(Event event) throws IOException {
                    ObjectNode json = mapper.createObjectNode().put("type", event.type()).put("sessionId", event.sessionId());
                    if (event.requestId() != null) json.put("requestId", event.requestId().toString());
                    if (event.turnId() != null) json.put("turnId", event.turnId());
                    if (event.sequence() != null) json.put("sequence", event.sequence());
                    JsonNode payload = mapper.valueToTree(event.payload());
                    if (event.payload() instanceof ErrorPayload error && error.activeSessionId() == null
                            && payload instanceof ObjectNode object) object.remove("activeSessionId");
                    json.set("payload", payload);
                    byte[] bytes = mapper.writeValueAsBytes(json);
                    if (bytes.length > SpeakingFrames.MAX_TEXT) throw new IOException("Speaking event exceeds frame limit");
                    socket.sendMessage(new TextMessage(bytes));
                }

                @Override public void send(byte[] frame) throws IOException {
                    socket.sendMessage(new BinaryMessage(frame));
                }

                @Override public void close() {
                    closeSocket(socket, CloseStatus.NORMAL);
                }
            }));
        } catch (SpeakingException failed) {
            socket.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        Connection connection = connection(socket);
        if (connection == null) return;
        if (message.getPayloadLength() > SpeakingFrames.MAX_TEXT) {
            connection.error(null, "FRAME_TOO_LARGE", "Speaking command is too large");
            return;
        }

        UUID request = null;
        Command command;
        try {
            var lease = (Lease) socket.getAttributes().get(LEASE);
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(message.asBytes());
            request = uuid(root.path("requestId"));
            if (!root.isObject() || !lease.sessionId().equals(root.path("sessionId").asString())
                    || !root.path("type").isString() || !root.path("payload").isObject()) throw new IllegalArgumentException();
            var type = CommandType.valueOf(root.path("type").asString());
            UUID turn = switch (type) {
                case END_TURN, CANCEL_TURN, RETRY_TURN -> uuid(root.path("turnId"));
                default -> null;
            };
            UUID client = type == CommandType.START_TURN ? uuid(root.path("payload").path("clientTurnId")) : null;
            command = new Command(type, request, turn, client);
        } catch (RuntimeException invalid) {
            connection.error(request, "INVALID_FRAME", "Invalid Speaking command");
            return;
        }
        connection.command(command);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) {
        Connection connection = connection(socket);
        if (connection == null) return;
        if (message.getPayloadLength() > SpeakingFrames.HEADER + SpeakingFrames.CHUNK) {
            connection.error(null, "FRAME_TOO_LARGE", "Speaking audio frame is too large");
            return;
        }
        SpeakingFrames.Learner frame;
        try {
            frame = SpeakingFrames.read(message.getPayload());
        } catch (IllegalArgumentException invalid) {
            connection.error(null, "INVALID_FRAME", "Invalid Speaking audio frame");
            return;
        }
        connection.audio(frame);
    }

    @Override
    public void handleTransportError(WebSocketSession socket, Throwable exception) {
        afterConnectionClosed(socket, CloseStatus.SERVER_ERROR);
        closeSocket(socket, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        var connection = connection(socket);
        if (connection != null) connection.close();
    }

    private static Connection connection(WebSocketSession socket) {
        return (Connection) socket.getAttributes().get(CONNECTION);
    }

    private static UUID uuid(JsonNode value) {
        if (!value.isString()) throw new IllegalArgumentException();
        String raw = value.asString();
        UUID uuid = UUID.fromString(raw);
        if (!uuid.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
        return uuid;
    }

    private static void closeSocket(WebSocketSession socket, CloseStatus status) {
        try {
            if (socket.isOpen()) socket.close(status);
        } catch (IOException ignored) {
            // The lease has already been released.
        }
    }
}
