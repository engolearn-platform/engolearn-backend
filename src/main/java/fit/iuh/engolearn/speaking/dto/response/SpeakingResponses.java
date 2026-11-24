package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class SpeakingResponses {
    private SpeakingResponses() {}

    public record Session(String sessionId, SpeakingScopeDto scope, Context context, SpeakingSessionStatus status,
                          SpeakingEndReason endReason, long controllerEpoch, String wsPath, Limits limits,
                          Instant createdAt, Instant startedAt, Instant endedAt, Opening opening,
                          int finalizedTurnCount, int committedTurnCount, long speechDurationMs,
                          SpeakingAssessmentStatus assessmentStatus, ActiveTurn activeTurn) {}
    public record Context(String title, String topicTitle, String topicItemTitle, CefrLevel level, String description,
                          String aiRole, String learnerRole) {}
    public record Limits(int maxTurns, long maxSessionMs, long maxTurnAudioMs) {}
    public record Opening(String reply, Hint hint, SpeakingAudioStatus audioStatus, boolean audioAvailable) {}
    public record Hint(String exampleEn, String explanationVi) {}
    public record Correction(SpeakingCorrectionType type, String original, String suggested, String explanationVi) {}
    public record ActiveTurn(String turnId, int sequence, String clientTurnId, SpeakingTurnStatus status) {}
    public record Current(String sessionId, SpeakingScopeDto scope, SpeakingSessionStatus status, long controllerEpoch) {}
    public record Availability(SpeakingScopeDto scope, boolean available, String reason, Current activeSession) {}
    public record Replacing(String status, String sessionId) {}
    public record Ticket(String ticket, long expiresInSeconds) {}
    public record Audio(URI url, long expiresInSeconds) {}
    public record Page<T>(List<T> items, int page, int size, boolean hasNext) {}
    public record Turn(String turnId, String clientTurnId, int sequence, SpeakingTurnStatus status, String transcript,
                       String reply, Hint hint, Correction correction, boolean learnerAudioAvailable,
                       boolean assistantAudioAvailable, SpeakingAudioStatus audioStatus, Instant replyDeliveredAt,
                       long speechDurationMs, String reason) {}
}
