package fit.iuh.engolearn.speaking.model;

import java.util.List;

public record DialogueDecision(
        Outcome outcome,
        ReasonCode reasonCode,
        DialogueAct acceptedAct,
        String spokenReplyToDeliver,
        String targetGoalId,
        SystemTrigger systemTrigger,
        SpeakingSession.Status resultingStatus,
        int finalizedTurnCount,
        int validTurnCount,
        int consecutiveEvaluationErrors,
        List<String> newlyAchievedGoalIds,
        boolean requiredMilestoneMet,
        boolean completionEligible,
        boolean allGoalsMet,
        boolean autoEndEligible
) {
    public enum Outcome { NORMAL, RECOVERY, TERMINAL, REJECT }

    public enum ReasonCode {
        NORMAL,
        INVALID_TURN,
        EVALUATION_ERROR,
        EVALUATION_ERRORS_3,
        ANSWER_FIRST_REQUIRED,
        CLARIFICATION_FALLBACK,
        WRAP_UP_NOT_AUTHORIZED,
        TARGET_ALREADY_ACHIEVED,
        DIALOGUE_NOT_AVAILABLE,
        SPOKEN_STOP_NOT_END,
        AUTO_ALL_GOALS_AND_MIN_TURNS,
        USER_END_ACTION,
        FINALIZED_TURN_LIMIT,
        SESSION_AGE_LIMIT,
        DAILY_QUOTA_EXHAUSTED,
        RECOVERY_EXPIRED,
        SERVICE_RESTART,
        SERVICE_UNAVAILABLE,
        REJECTED_TERMINAL,
        REJECTED_NO_PROPOSAL
    }

    public enum SystemTrigger {
        NONE,
        USER_END,
        FINALIZED_TURN_LIMIT,
        SESSION_AGE,
        DAILY_QUOTA,
        RECOVERY_EXPIRED,
        SERVICE_RESTART,
        SERVICE_UNAVAILABLE
    }

    public DialogueDecision {
        newlyAchievedGoalIds = newlyAchievedGoalIds == null ? List.of() : List.copyOf(newlyAchievedGoalIds);
    }
}
