package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.service.SpeakingRuntime.Lease;
import fit.iuh.engolearn.speaking.utils.SpeakingFrames;

import java.io.IOException;
import java.util.UUID;

public interface SpeakingRealtime {
    enum CommandType { START_TURN, END_TURN, CANCEL_TURN, RETRY_TURN, SYNC_SESSION }
    record Command(CommandType type, UUID requestId, UUID turnId, UUID clientTurnId) {}
    record Event(String type, String sessionId, UUID requestId, String turnId, Integer sequence, Object payload) {}
    record ErrorPayload(String code, boolean retryable, String message, String activeSessionId) {}

    interface Channel {
        void send(Event event) throws IOException;
        void send(byte[] frame) throws IOException;
        void close();
    }

    interface Connection {
        void command(Command command);
        void audio(SpeakingFrames.Learner frame);
        void error(UUID requestId, String code, String message);
        void close();
    }

    Connection connect(Lease lease, Channel channel);
    void openSession(String sessionId);
    void sessionChanged(String sessionId);
    void recover();
}
