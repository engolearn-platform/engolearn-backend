package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.DialogueAct;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.SpeakingSession;

import java.util.List;

public record SpeakingDemoDecisionEvent(
        DialogueDecision.Outcome outcome,
        DialogueDecision.ReasonCode reasonCode,
        DialogueAct acceptedAct,
        SpeakingSession.Status resultingStatus,
        int finalizedTurnCount,
        int validTurnCount,
        int consecutiveEvaluationErrors,
        List<String> newlyAchievedGoalIds,
        boolean completionEligible,
        boolean autoEndEligible
) {
    public SpeakingDemoDecisionEvent {
        newlyAchievedGoalIds = newlyAchievedGoalIds == null ? List.of() : List.copyOf(newlyAchievedGoalIds);
    }

    public static SpeakingDemoDecisionEvent from(DialogueDecision decision) {
        return new SpeakingDemoDecisionEvent(decision.outcome(), decision.reasonCode(), decision.acceptedAct(),
                decision.resultingStatus(), decision.finalizedTurnCount(), decision.validTurnCount(),
                decision.consecutiveEvaluationErrors(), decision.newlyAchievedGoalIds(),
                decision.completionEligible(), decision.autoEndEligible());
    }
}
