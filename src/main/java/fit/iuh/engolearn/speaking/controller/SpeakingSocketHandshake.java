package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime.Lease;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SpeakingSocketHandshake implements HandshakeInterceptor {
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern PATH = Pattern.compile("/ws/speaking/(" + UUID + ")");
    private static final Pattern QUERY = Pattern.compile("ticket=(" + UUID + UUID + ")");
    private final SpeakingRuntime runtime;
    private final ObjectMapper mapper;
    private final List<String> origins;
    private final ThreadLocal<Lease> pending = new ThreadLocal<>();

    public SpeakingSocketHandshake(SpeakingRuntime runtime, ObjectMapper mapper, List<String> origins) {
        this.runtime = runtime;
        this.mapper = mapper;
        this.origins = List.copyOf(origins);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) throws IOException {
        HttpHeaders headers = request.getHeaders();
        if (headers.getOrigin() == null || !origins.contains(headers.getOrigin())) {
            return reject(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "The Speaking socket origin is denied", false);
        }
        var path = PATH.matcher(request.getURI().getRawPath());
        var ticket = QUERY.matcher(request.getURI().getRawQuery() == null ? "" : request.getURI().getRawQuery());
        if (!path.matches() || !ticket.matches()) {
            return reject(response, HttpStatus.UNAUTHORIZED, "INVALID_TICKET", "A valid Speaking ticket is required", true);
        }
        if (!validUpgrade(headers)) {
            return reject(response, HttpStatus.BAD_REQUEST, "INVALID_FRAME", "A WebSocket upgrade is required", false);
        }
        try {
            var lease = runtime.consumeTicket(path.group(1), ticket.group(1));
            pending.set(lease);
            attributes.put(SpeakingSocketHandler.LEASE, lease);
            return true;
        } catch (SpeakingException refused) {
            return reject(response, refused.status(), refused.code(), refused.getMessage(), refused.retryable());
        }
    }

    private static boolean validUpgrade(HttpHeaders headers) {
        if (!"websocket".equalsIgnoreCase(headers.getUpgrade()) || !"13".equals(headers.getFirst("Sec-WebSocket-Version"))
                || headers.getConnection().stream().flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .noneMatch(value -> "upgrade".equalsIgnoreCase(value.strip()))) return false;
        try {
            String key = headers.getFirst("Sec-WebSocket-Key");
            return key != null && Base64.getDecoder().decode(key).length == 16;
        } catch (IllegalArgumentException badKey) {
            return false;
        }
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status, String code, String detail, boolean retryable)
            throws IOException {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        problem.setProperty("retryable", retryable);
        response.getBody().write(mapper.writeValueAsBytes(problem));
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception failure) {
        Lease lease = pending.get();
        pending.remove();
        if (failure != null && lease != null) runtime.releaseLease(lease);
    }
}
