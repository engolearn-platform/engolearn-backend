package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private final Duration connectTimeout;
    private final Duration finalizationTimeout;
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();

    private static final class Stream {
        private final Listener listener;
        private volatile WebSocket socket;
        private volatile CompletableFuture<String> ending;
        private String latest = "";
        private int order = -1;
        private final TreeMap<Integer, String> finalized = new TreeMap<>();
        private volatile boolean endpointSent;
        private long acceptedAudioFrames;
        private volatile boolean active;
        private final StringBuilder buffer = new StringBuilder();
        private final AtomicInteger pendingFrames = new AtomicInteger();
        private CompletableFuture<WebSocket> outbound = CompletableFuture.completedFuture(null);

        private Stream(Listener listener) { this.listener = listener; }
    }

    @Autowired
    public AssemblyAiSpeechStream(ObjectMapper mapper, SpeakingProviderProperties properties) {
        this(mapper, properties.getAssemblyai().getApiKey(), properties.getAssemblyai().getEndpoint(),
                properties.getAssemblyai().getModel(), properties.getAssemblyai().getConnectTimeout(),
                properties.getAssemblyai().getFinalizationTimeout());
    }

    AssemblyAiSpeechStream(ObjectMapper mapper, String apiKey, String endpoint, String model,
            Duration connectTimeout, Duration finalizationTimeout) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.connectTimeout = connectTimeout;
        this.finalizationTimeout = finalizationTimeout;
        requirePositive(connectTimeout, "AssemblyAI connect timeout");
        requirePositive(finalizationTimeout, "AssemblyAI finalization timeout");
    }

    @Override
    public CompletableFuture<Void> open(String sessionId, Listener listener) {
        if (apiKey.isBlank()) return CompletableFuture.failedFuture(
                new IllegalStateException("AssemblyAI API key is not configured"));
        Stream state = new Stream(listener);
        Stream previous = streams.put(sessionId, state);
        if (previous != null) terminate(previous);
        String separator = endpoint.contains("?") ? "&" : "?";
        URI uri = URI.create(endpoint + separator
                + "sample_rate=16000&encoding=pcm_s16le&speech_model=" + model);
        return client().newWebSocketBuilder().connectTimeout(connectTimeout).header("Authorization", apiKey)
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket socket) { socket.request(1); }

                    @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        synchronized (state.buffer) {
                            if (state.buffer.length() + data.length() > 64 * 1024) {
                                state.buffer.setLength(0);
                                fail(state, "STT response is too large");
                                socket.abort();
                                return CompletableFuture.completedFuture(null);
                            }
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
                }).thenAccept(socket -> {
                    state.socket = socket;
                    if (streams.get(sessionId) != state) terminate(state);
                }).whenComplete((ok, error) -> {
                    if (error != null) streams.remove(sessionId, state);
                });
    }

    private void receive(Stream state, String message) {
        try {
            JsonNode event = mapper.readTree(message);
            String interim = null;
            String finished = null;
            CompletableFuture<String> ending;
            synchronized (state) {
                if (!"Turn".equalsIgnoreCase(event.path("type").asText("")) || !state.active) return;
                int order = event.path("turn_order").asInt(Math.max(0, state.order));
                boolean terminal = event.path("end_of_turn").asBoolean(false);
                if (order < 0 || order < state.order || state.finalized.containsKey(order)) return;
                if (order > state.order) {
                    state.order = order;
                    state.latest = "";
                }
                String transcript = event.path("transcript").asText("");
                if (!transcript.isBlank()) {
                    state.latest = transcript;
                    interim = combined(state);
                }
                ending = state.ending;
                if (terminal) {
                    state.finalized.put(order, state.latest);
                    state.latest = "";
                    if (ending != null && state.endpointSent) finished = combined(state);
                }
            }
            if (interim != null) state.listener.onInterim(interim);
            if (finished != null) ending.complete(finished);
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
        String prior = state.finalized.values().stream().filter(text -> !text.isBlank()).collect(Collectors.joining(" "));
        if (prior.isEmpty()) return state.latest;
        return state.latest.isEmpty() ? prior : prior + " " + state.latest;
    }

    @Override public void beginTurn(String sessionId) {
        Stream state = ready(sessionId);
        synchronized (state) {
            if (state.active) throw new IllegalStateException("STT turn is already active");
            state.latest = "";
            state.finalized.clear();
            state.ending = null;
            state.order = -1;
            state.endpointSent = false;
            state.acceptedAudioFrames = 0;
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
            state.acceptedAudioFrames++;
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
            if (state.acceptedAudioFrames == 0 && !state.finalized.isEmpty() && state.latest.isEmpty()) {
                state.active = false;
                return CompletableFuture.completedFuture(combined(state));
            }
            state.ending = new CompletableFuture<>();
            state.outbound = state.outbound.thenCompose(ignored -> {
                state.endpointSent = true;
                return state.socket.sendText("{\"type\":\"ForceEndpoint\"}", true);
            });
            state.outbound.whenComplete((ignored, error) -> {
                if (error != null) fail(state, "STT endpoint request failed");
            });
            return state.ending.orTimeout(finalizationTimeout.toMillis(), TimeUnit.MILLISECONDS).handle((text, error) -> {
                synchronized (state) {
                    state.active = false;
                    state.ending = null;
                    if (error == null) return text;
                    // A provider can have already endpointed all speech before a forced endpoint.
                    if (error instanceof java.util.concurrent.TimeoutException && state.endpointSent
                            && state.latest.isBlank() && !combined(state).isBlank()) return combined(state);
                    throw new java.util.concurrent.CompletionException(error);
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
            state.finalized.clear();
        }
        state.socket.sendText("{\"type\":\"ForceEndpoint\"}", true);
    }

    @Override public void close(String sessionId) {
        Stream state = streams.remove(sessionId);
        if (state != null) terminate(state);
    }

    private void terminate(Stream state) {
        WebSocket socket = state.socket;
        state.active = false;
        if (socket == null) return;
        synchronized (state) {
            state.outbound.handle((ignored, failure) -> socket)
                    .thenCompose(active -> active.sendText("{\"type\":\"Terminate\"}", true))
                    .orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((ignored, error) -> {
                        if (error == null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
                        else socket.abort();
                    });
        }
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
                if (client == null) client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
                current = client;
            }
        }
        return current;
    }

    private static void requirePositive(Duration timeout, String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
