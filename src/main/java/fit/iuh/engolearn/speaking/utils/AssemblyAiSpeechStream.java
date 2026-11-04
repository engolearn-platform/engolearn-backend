package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AssemblyAiSpeechStream implements SpeechStreamPort {
    private volatile HttpClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();

    private static final class Stream {
        private final Listener listener;
        private volatile WebSocket socket;
        private volatile CompletableFuture<String> ending;
        private volatile String latest = "";
        private final StringBuilder finalized = new StringBuilder();
        private volatile boolean active;
        private final StringBuilder buffer = new StringBuilder();
        private final AtomicInteger pendingFrames = new AtomicInteger();
        private CompletableFuture<WebSocket> outbound = CompletableFuture.completedFuture(null);

        private Stream(Listener listener) { this.listener = listener; }
    }

    public AssemblyAiSpeechStream(ObjectMapper mapper,
            @Value("${engolearn.speaking.assemblyai.api-key:}") String apiKey,
            @Value("${engolearn.speaking.assemblyai.endpoint:wss://streaming.assemblyai.com/v3/ws}") String endpoint,
            @Value("${engolearn.speaking.assemblyai.model:universal-3-6-pro}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
    }

    @Override
    public CompletableFuture<Void> open(String sessionId, Listener listener) {
        if (apiKey.isBlank()) return CompletableFuture.failedFuture(
                new IllegalStateException("AssemblyAI API key is not configured"));
        Stream state = new Stream(listener);
        Stream previous = streams.put(sessionId, state);
        if (previous != null && previous.socket != null) {
            previous.active = false;
            previous.socket.sendClose(WebSocket.NORMAL_CLOSURE, "new controller");
        }
        String separator = endpoint.contains("?") ? "&" : "?";
        URI uri = URI.create(endpoint + separator
                + "sample_rate=16000&encoding=pcm_s16le&speech_model=" + model);
        return client().newWebSocketBuilder().header("Authorization", apiKey)
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket socket) { socket.request(1); }

                    @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        synchronized (state.buffer) {
                            state.buffer.append(data);
                            if (last) {
                                String message = state.buffer.toString();
                                state.buffer.setLength(0);
                                receive(state, message);
                            }
                        }
                        socket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
                        fail(state, "STT connection closed");
                        streams.remove(sessionId, state);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override public void onError(WebSocket socket, Throwable error) {
                        fail(state, "STT connection failed");
                        streams.remove(sessionId, state);
                    }
                }).thenAccept(socket -> state.socket = socket).whenComplete((ok, error) -> {
                    if (error != null) streams.remove(sessionId, state);
                });
    }

    private void receive(Stream state, String message) {
        try {
            JsonNode event = mapper.readTree(message);
            synchronized (state) {
                if (!"Turn".equalsIgnoreCase(event.path("type").asText("")) || !state.active) return;
                String transcript = event.path("transcript").asText("").trim();
                if (!transcript.isEmpty()) {
                    state.latest = transcript;
                    state.listener.onInterim(combined(state));
                }
                if (event.path("end_of_turn").asBoolean(false)) {
                    if (!state.latest.isEmpty()) {
                        if (!state.finalized.isEmpty()) state.finalized.append(' ');
                        state.finalized.append(state.latest);
                        state.latest = "";
                    }
                    if (state.ending != null) state.ending.complete(state.finalized.toString());
                }
            }
        } catch (Exception ex) {
            fail(state, "STT response could not be read");
        }
    }

    private void fail(Stream state, String message) {
        state.listener.onFailure(message);
        CompletableFuture<String> ending = state.ending;
        if (ending != null) ending.completeExceptionally(new IllegalStateException(message));
    }

    private String combined(Stream state) {
        if (state.finalized.isEmpty()) return state.latest;
        if (state.latest.isEmpty()) return state.finalized.toString();
        return state.finalized + " " + state.latest;
    }

    @Override public void beginTurn(String sessionId) {
        Stream state = ready(sessionId);
        synchronized (state) {
            if (state.active) throw new IllegalStateException("STT turn is already active");
            state.latest = "";
            state.finalized.setLength(0);
            state.ending = null;
            state.active = true;
        }
    }

    @Override public void send(String sessionId, byte[] pcm) {
        Stream state = ready(sessionId);
        if (!state.active || pcm.length == 0 || pcm.length > 6400 || pcm.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid PCM frame or no active turn");
        }
        if (state.pendingFrames.incrementAndGet() > 10) {
            state.pendingFrames.decrementAndGet();
            throw new IllegalStateException("STT connection is too slow");
        }
        synchronized (state) {
            state.outbound = state.outbound.thenCompose(ignored ->
                    state.socket.sendBinary(ByteBuffer.wrap(pcm), true));
            state.outbound.whenComplete((ignored, error) -> {
                state.pendingFrames.decrementAndGet();
                if (error != null) fail(state, "STT audio delivery failed");
            });
        }
    }

    @Override public CompletableFuture<String> endTurn(String sessionId) {
        Stream state = ready(sessionId);
        synchronized (state) {
            if (!state.active || state.ending != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No active STT turn"));
            }
            if (!state.finalized.isEmpty() && state.latest.isEmpty()) {
                state.active = false;
                return CompletableFuture.completedFuture(state.finalized.toString());
            }
            state.ending = new CompletableFuture<>();
            state.outbound = state.outbound.thenCompose(ignored ->
                    state.socket.sendText("{\"type\":\"ForceEndpoint\"}", true));
            state.outbound.whenComplete((ignored, error) -> {
                if (error != null) fail(state, "STT endpoint request failed");
            });
            return state.ending.orTimeout(6, TimeUnit.SECONDS).whenComplete((text, error) -> {
                synchronized (state) {
                    state.active = false;
                    state.ending = null;
                }
            });
        }
    }

    @Override public void cancelTurn(String sessionId) {
        Stream state = ready(sessionId);
        synchronized (state) {
            state.active = false;
            state.ending = null;
            state.latest = "";
            state.finalized.setLength(0);
        }
        state.socket.sendText("{\"type\":\"ForceEndpoint\"}", true);
    }

    @Override public void close(String sessionId) {
        Stream state = streams.remove(sessionId);
        if (state == null || state.socket == null) return;
        state.active = false;
        state.socket.sendText("{\"type\":\"Terminate\"}", true);
        state.socket.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
    }

    private Stream ready(String sessionId) {
        Stream state = streams.get(sessionId);
        if (state == null || state.socket == null) throw new IllegalStateException("STT is not connected");
        return state;
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = HttpClient.newHttpClient();
                current = client;
            }
        }
        return current;
    }
}
