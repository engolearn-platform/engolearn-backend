package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.Session;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.Turn;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.*;
import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpeakingRuntime {
    record Lease(String sessionId, String ownerId, long epoch, String leaseId) {}
    record TurnWork(String sessionId, String turnId, String token) {}

    Lease consumeTicket(String sessionId, String ticket);
    Session state(Lease lease);
    void releaseLease(Lease lease);
    void closeRetryWindow(Lease lease, UUID nextClientTurnId);
    Turn admit(Lease lease, UUID clientTurnId);
    void recordingStarted(Lease lease, String turnId);
    Turn cancel(Lease lease, String turnId);
    default TurnWork seal(Lease lease, String turnId, SpeakingMediaVo audio, long durationMs) {
        return seal(lease, turnId, audio, durationMs, true);
    }
    TurnWork seal(Lease lease, String turnId, SpeakingMediaVo audio, long durationMs, boolean captureComplete);
    TurnWork retry(Lease lease, String turnId);
    void transcript(TurnWork work, String original);
    Turn settle(TurnWork work, SpeakingTurnStatus result, String reason, String reply, SpeakingHintVo hint,
                SpeakingCorrectionVo correction);
    SpeakingSession runtimeSession(String sessionId);
    boolean completeOpening(String sessionId, SpeakingOpeningVo opening);
    String claimOpening(String sessionId, Instant until);
    boolean completeOpening(String sessionId, String token, SpeakingOpeningVo opening);
    boolean openingAudio(String sessionId, String token, SpeakingAudioStatus status, SpeakingMediaVo audio);
    SpeakingTurnVo workState(TurnWork work);
    void attachAudio(TurnWork work, SpeakingMediaVo audio);
    SpeakingTurnVo finalizedAudioEvidence(String sessionId, String turnId);
    void transcript(TurnWork work, String original, long successfulSttWaitMs);
    Turn commit(TurnWork work, Result result, long generationWaitMs);
    List<SpeakingTurnVo> conversationHistory(TurnWork work);
    void recover();
}
