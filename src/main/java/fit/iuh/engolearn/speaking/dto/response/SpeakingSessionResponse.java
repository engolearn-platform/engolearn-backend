package fit.iuh.engolearn.speaking.dto.response;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingOpening;

import java.time.Instant;
import java.util.List;

public record SpeakingSessionResponse(
        String id, SpeakingContentSnapshot content, SpeakingSession.Status status,
        SpeakingSession.EndReason endReason, Instant startedAt, Instant expiresAt,
        Instant endedAt, int validTurnCount, int committedTurnCount,
        List<String> achievedGoalIds, List<GoalResult> goalResults,
        boolean requiredGoalsMet, boolean completionEligible,
        long controllerEpoch, Long revision,
        boolean canStartTurn, Opening opening, List<Turn> turns
) {
    public record Opening(SpeakingOpening.Status status, String reply, String hintExampleEn,
                          boolean aiAudioAvailable) {}
    public record GoalResult(String goalId, SpeakingGoal.Priority priority,
                             boolean achieved, String evidenceQuote) {}
    public record Turn(String id, int sequence, String transcript, Instant committedAt,
                       boolean valid, SpeakingTurn.EvaluationState evaluationState,
                       String aiReply, Assessment assessment,
                       boolean recordingAvailable, boolean aiAudioAvailable) {}

    public record Assessment(String id, String transcript, String criteriaVersion,
                             List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
                             List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
                             List<SpeakingAssessmentRecord.Correction> corrections,
                             String hint, List<String> feedback,
                             String hintExampleEn, String hintLeadVi) {}
}
