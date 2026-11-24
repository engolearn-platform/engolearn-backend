package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingRealtimeEventShapeTest {
    @Test void connectedAndStartEventsCarryCurrentSnapshotAndControllerMetadata() {
        SpeakingSession session = SpeakingSession.builder().id("session").learnerId("learner")
                .controllerEpoch(7).status(SpeakingSession.Status.ACTIVE).build();
        SpeakingSessionResponse snapshot = new SpeakingSessionResponse("session", null,
                SpeakingSession.Status.ACTIVE, null, Instant.EPOCH, Instant.EPOCH, null,
                0, 0, List.of(), List.of(), false, false, 7, 12L, true, null, List.of());
        SpeakingSessionService sessions = new SpeakingSessionService(null, null, null, null,
                null, null, null, null, null) {
            @Override public SpeakingSession consumeTicket(String ticket, String sessionId) { return session; }
            @Override public SpeakingSessionResponse socketSnapshot(String id, String learnerId) { return snapshot; }
            @Override public SpeakingSession requireSocketController(String id, String learnerId, long epoch) {
                return session;
            }
        };
        SpeechStreamPort stt = new SpeechStreamPort() {
            @Override public CompletableFuture<Void> open(String id, Listener listener) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void beginTurn(String id) {}
            @Override public void send(String id, byte[] pcm) {}
            @Override public CompletableFuture<String> endTurn(String id) { return CompletableFuture.completedFuture(""); }
            @Override public void cancelTurn(String id) {}
            @Override public void close(String id) {}
        };
        var events = new ArrayList<Map<String, Object>>();
        var realtime = new SpeakingRealtimeService(sessions, stt, null, null);

        realtime.open("connection", "session", "ticket", events::add).join();
        realtime.start("connection", "turn-1", 7);

        assertEquals(2, events.size());
        assertEquals("CONNECTED", events.get(0).get("type"));
        assertEquals("session", events.get(0).get("sessionId"));
        assertSame(snapshot, events.get(0).get("snapshot"));
        assertNull(events.get(0).get("turnId"));
        assertEquals("STT_READY", events.get(1).get("type"));
        assertEquals("turn-1", events.get(1).get("turnId"));
        for (var event : events) {
            assertEquals(7L, event.get("controllerEpoch"));
            assertEquals(12L, event.get("revision"));
        }
    }
}
