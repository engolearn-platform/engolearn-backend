package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.Session;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.Turn;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.*;
import java.util.UUID;

public interface SpeakingRuntime {
    record Lease(String sessionId, String ownerId, long epoch, String leaseId) {}
    record TurnWork(String sessionId, String turnId, String token) {}

    Lease consumeTicket(String sessionId, String ticket);
    Session state(Lease lease);
    void releaseLease(Lease lease);
    Turn admit(Lease lease, UUID clientTurnId);
    void recordingStarted(Lease lease, String turnId);
    Turn cancel(Lease lease, String turnId);
    TurnWork seal(Lease lease, String turnId, SpeakingMediaVo audio, long durationMs);
    TurnWork retry(Lease lease, String turnId);
    void transcript(TurnWork work, String original);
    Turn settle(TurnWork work, SpeakingTurnStatus result, String reason, String reply, SpeakingHintVo hint,
                SpeakingCorrectionVo correction);
    SpeakingSession runtimeSession(String sessionId);
    boolean completeOpening(String sessionId, SpeakingOpeningVo opening);
    void recover();
}
