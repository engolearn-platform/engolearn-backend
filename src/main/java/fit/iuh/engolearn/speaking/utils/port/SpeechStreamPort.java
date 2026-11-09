package fit.iuh.engolearn.speaking.utils.port;

import java.util.concurrent.CompletableFuture;

public interface SpeechStreamPort {
    interface Listener {
        void onInterim(String text);
        void onFailure(String message);
    }

    CompletableFuture<Void> open(String sessionId, Listener listener);
    void beginTurn(String sessionId);
    void send(String sessionId, byte[] pcm);
    CompletableFuture<String> endTurn(String sessionId);
    void cancelTurn(String sessionId);
    void close(String sessionId);
}
