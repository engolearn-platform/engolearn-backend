package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssemblyAiSpeechStreamTest {
    @Test void missingCredentialsFailWhenStreamIsOpened() {
        AssemblyAiSpeechStream stream = new AssemblyAiSpeechStream(new ObjectMapper(), "", "wss://example.test",
                "model", Duration.ofSeconds(3), Duration.ofSeconds(2));

        var error = assertThrows(CompletionException.class, () -> stream.open("session", new SpeechStreamPort.Listener() {
            @Override public void onInterim(String text) { }
            @Override public void onFailure(String message) { }
        }).join());
        assertEquals("AssemblyAI API key is not configured", error.getCause().getMessage());
    }

    @Test void endTurnUsesFinalTranscriptAlreadyReceived() throws Exception {
        Fixture fixture = new Fixture();
        fixture.stream.beginTurn("session");
        fixture.turn("Hello.", true);

        assertEquals("Hello.", fixture.stream.endTurn("session").join());
        verify(fixture.socket, never()).sendText(anyString(), eq(true));
    }

    @Test void endTurnCollectsFinalSegmentsAndWaitsForCurrentPartial() throws Exception {
        Fixture fixture = new Fixture();
        fixture.stream.beginTurn("session");
        fixture.turn("Hello.", true);
        fixture.turn("How are", false);

        CompletableFuture<String> ending = fixture.stream.endTurn("session");

        assertFalse(ending.isDone());
        verify(fixture.socket).sendText("{\"type\":\"ForceEndpoint\"}", true);
        fixture.turn("How are you?", true);
        assertEquals("Hello. How are you?", ending.join());
        assertEquals(List.of("Hello.", "Hello. How are", "Hello. How are you?"), fixture.interims);
    }

    @Test void endTurnSendsForceEndpointAfterPendingAudio() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<WebSocket> audioSent = new CompletableFuture<>();
        when(fixture.socket.sendBinary(any(ByteBuffer.class), eq(true))).thenReturn(audioSent);
        fixture.stream.beginTurn("session");
        fixture.stream.send("session", new byte[3200]);

        CompletableFuture<String> ending = fixture.stream.endTurn("session");

        verify(fixture.socket, never()).sendText(anyString(), eq(true));
        audioSent.complete(fixture.socket);
        verify(fixture.socket).sendText("{\"type\":\"ForceEndpoint\"}", true);
        fixture.turn("Hello.", true);
        assertEquals("Hello.", ending.join());
    }

    @Test void usesTheConfiguredStreamEndpointModelAndFinalizationTimeout() throws Exception {
        Fixture fixture = new Fixture(Duration.ofMillis(100));
        verify(fixture.builder).buildAsync(eq(URI.create("wss://example.test/v3/ws?sample_rate=16000&encoding=pcm_s16le&speech_model=test-model")), any());
        fixture.stream.beginTurn("session");
        var error = assertThrows(CompletionException.class, () -> fixture.stream.endTurn("session").join());
        assertEquals(TimeoutException.class, error.getCause().getClass());
    }

    private static final class Fixture {
        private final AssemblyAiSpeechStream stream;
        private final WebSocket.Builder builder = mock(WebSocket.Builder.class);
        private final WebSocket socket = mock(WebSocket.class);
        private final List<String> interims = new ArrayList<>();
        private final WebSocket.Listener receiver;

        private Fixture() throws Exception {
            this(Duration.ofSeconds(2));
        }

        private Fixture(Duration timeout) throws Exception {
            stream = new AssemblyAiSpeechStream(new ObjectMapper(), "test-key", "wss://example.test/v3/ws",
                    "test-model", Duration.ofSeconds(3), timeout);
            HttpClient client = mock(HttpClient.class);
            AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
            when(client.newWebSocketBuilder()).thenReturn(builder);
            when(builder.header(anyString(), anyString())).thenReturn(builder);
            when(builder.buildAsync(any(), any())).thenAnswer(invocation -> {
                listener.set(invocation.getArgument(1));
                return CompletableFuture.completedFuture(socket);
            });
            when(socket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
            Field clientField = AssemblyAiSpeechStream.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(stream, client);
            stream.open("session", new SpeechStreamPort.Listener() {
                @Override public void onInterim(String text) { interims.add(text); }
                @Override public void onFailure(String message) { throw new AssertionError(message); }
            }).join();
            receiver = listener.get();
        }

        private void turn(String transcript, boolean finalTurn) {
            String message = "{\"type\":\"Turn\",\"transcript\":\"" + transcript
                    + "\",\"end_of_turn\":" + finalTurn + "}";
            receiver.onText(socket, message, true).toCompletableFuture().join();
        }
    }
}
