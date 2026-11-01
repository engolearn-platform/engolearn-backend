package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.ConversationStateView;
import fit.iuh.engolearn.speaking.model.DialogueAct;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.SpeakingAiDraftResponseV1;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConversationManager {
    public static final class SystemTrigger {
        public static final DialogueDecision.SystemTrigger NONE = DialogueDecision.SystemTrigger.NONE;
        public static final DialogueDecision.SystemTrigger USER_END = DialogueDecision.SystemTrigger.USER_END;
        public static final DialogueDecision.SystemTrigger FINALIZED_TURN_LIMIT = DialogueDecision.SystemTrigger.FINALIZED_TURN_LIMIT;
        public static final DialogueDecision.SystemTrigger SESSION_AGE = DialogueDecision.SystemTrigger.SESSION_AGE;
        public static final DialogueDecision.SystemTrigger DAILY_QUOTA = DialogueDecision.SystemTrigger.DAILY_QUOTA;
        public static final DialogueDecision.SystemTrigger RECOVERY_EXPIRED = DialogueDecision.SystemTrigger.RECOVERY_EXPIRED;
        public static final DialogueDecision.SystemTrigger SERVICE_RESTART = DialogueDecision.SystemTrigger.SERVICE_RESTART;
        public static final DialogueDecision.SystemTrigger SERVICE_UNAVAILABLE = DialogueDecision.SystemTrigger.SERVICE_UNAVAILABLE;

        private SystemTrigger() {}
    }

    public DialogueDecision decide(ValidatedTurnProposal proposal, ConversationStateView state,
            SpeakingContentSnapshot snapshot, DialogueDecision.SystemTrigger trigger) {
        ConversationStateView.Completion base = state.completionFor(snapshot);
        if (state.status() == SpeakingSession.Status.COMPLETED || state.status() == SpeakingSession.Status.INCOMPLETE) {
            return decision(DialogueDecision.Outcome.REJECT, DialogueDecision.ReasonCode.REJECTED_TERMINAL,
                    state, base, null, null, null, null, null);
        }
        if (trigger == DialogueDecision.SystemTrigger.SERVICE_UNAVAILABLE
                || (proposal == null && trigger != null && trigger != DialogueDecision.SystemTrigger.NONE)) {
            return lifecycleDecision(state, base, trigger);
        }
        if (proposal == null) {
            return decision(DialogueDecision.Outcome.REJECT, DialogueDecision.ReasonCode.REJECTED_NO_PROPOSAL,
                    state, base, null, null, null, null, null);
        }
        if (proposal.validityOutcome() == ValidatedTurnProposal.ValidityOutcome.INVALID) {
            return decision(DialogueDecision.Outcome.RECOVERY, DialogueDecision.ReasonCode.INVALID_TURN,
                    state, base, null, null, null, null, null);
        }
        if (proposal.validityOutcome() == ValidatedTurnProposal.ValidityOutcome.EVALUATION_FAILED) {
            int errors = state.consecutiveEvaluationErrors() + 1;
            DialogueDecision.ReasonCode reason = errors >= 3
                    ? DialogueDecision.ReasonCode.EVALUATION_ERRORS_3
                    : DialogueDecision.ReasonCode.EVALUATION_ERROR;
            ConversationStateView projected = new ConversationStateView(state.status(), state.finalizedTurnCount(),
                    state.validTurnCount(), errors, state.achievedGoalIds(), state.closingStartedAt(),
                    state.closingReason(), state.controllerEpoch(), state.revision());
            ConversationStateView.Completion completion = projected.completionFor(snapshot);
            DialogueDecision.SystemTrigger closingTrigger = effectiveClosingTrigger(state, trigger);
            if (closingTrigger != DialogueDecision.SystemTrigger.NONE) {
                return lifecycleDecision(projected, completion, closingTrigger, List.of());
            }
            return decision(errors >= 3 ? DialogueDecision.Outcome.TERMINAL : DialogueDecision.Outcome.RECOVERY,
                    reason, projected, completion, null, null, null,
                    errors >= 3
                            ? (completion.completionEligible() ? SpeakingSession.Status.COMPLETED : SpeakingSession.Status.INCOMPLETE)
                            : SpeakingSession.Status.ACTIVE, null);
        }

        Set<String> pinnedGoalIds = snapshot.goals().stream().map(SpeakingGoal::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> previous = state.achievedGoalIds();
        List<String> newlyAchieved = proposal.goalEvidence().stream().map(evidence -> evidence.goalId())
                .filter(pinnedGoalIds::contains).filter(id -> !previous.contains(id)).distinct().toList();
        ConversationStateView projected = state.projected(1, newlyAchieved);
        ConversationStateView.Completion completion = projected.completionFor(snapshot);

        DialogueDecision.SystemTrigger closingTrigger = effectiveClosingTrigger(state, trigger);
        if (closingTrigger != DialogueDecision.SystemTrigger.NONE) {
            return lifecycleDecision(projected, completion, closingTrigger, newlyAchieved);
        }

        if (completion.autoEndEligible()) {
            return decision(DialogueDecision.Outcome.TERMINAL,
                    DialogueDecision.ReasonCode.AUTO_ALL_GOALS_AND_MIN_TURNS, projected, completion,
                    null, null, newlyAchieved, SpeakingSession.Status.COMPLETED, null);
        }

        SpeakingAiDraftResponseV1.Dialogue dialogue = proposal.dialogue();
        if (dialogue == null) {
            return decision(DialogueDecision.Outcome.NORMAL, DialogueDecision.ReasonCode.DIALOGUE_NOT_AVAILABLE,
                    projected, completion, null, null, newlyAchieved, SpeakingSession.Status.ACTIVE, null);
        }
        if (dialogue.proposedAct() == DialogueAct.WRAP_UP) {
            return decision(DialogueDecision.Outcome.NORMAL, DialogueDecision.ReasonCode.WRAP_UP_NOT_AUTHORIZED,
                    projected, completion, null, null, newlyAchieved, SpeakingSession.Status.ACTIVE, null);
        }
        if (proposal.userIntent() == SpeakingAiDraftResponseV1.UserIntent.QUESTION
                && dialogue.proposedAct() != DialogueAct.ANSWER_AND_ASK
                && dialogue.proposedAct() != DialogueAct.CLARIFY) {
            return decision(DialogueDecision.Outcome.NORMAL, DialogueDecision.ReasonCode.ANSWER_FIRST_REQUIRED,
                    projected, completion, null, null, newlyAchieved, SpeakingSession.Status.ACTIVE, null);
        }
        if (dialogue.proposedAct() == DialogueAct.GUIDE_GOAL
                && (dialogue.targetGoalId() == null || !pinnedGoalIds.contains(dialogue.targetGoalId())
                || projected.achievedGoalIds().contains(dialogue.targetGoalId()))) {
            return decision(DialogueDecision.Outcome.NORMAL, DialogueDecision.ReasonCode.TARGET_ALREADY_ACHIEVED,
                    projected, completion, null, null, newlyAchieved, SpeakingSession.Status.ACTIVE, null);
        }
        DialogueDecision.ReasonCode reason = proposal.userIntent()
                == SpeakingAiDraftResponseV1.UserIntent.CLOSING_UTTERANCE
                ? DialogueDecision.ReasonCode.SPOKEN_STOP_NOT_END : DialogueDecision.ReasonCode.NORMAL;
        return decision(DialogueDecision.Outcome.NORMAL, reason, projected, completion, dialogue.proposedAct(),
                dialogue.spokenReply(), dialogue.targetGoalId(), newlyAchieved, SpeakingSession.Status.ACTIVE, null);
    }

    private DialogueDecision lifecycleDecision(ConversationStateView state, ConversationStateView.Completion completion,
            DialogueDecision.SystemTrigger trigger) {
        return lifecycleDecision(state, completion, trigger, List.of());
    }

    private DialogueDecision lifecycleDecision(ConversationStateView state, ConversationStateView.Completion completion,
            DialogueDecision.SystemTrigger trigger, List<String> newlyAchieved) {
        if (trigger == DialogueDecision.SystemTrigger.SERVICE_UNAVAILABLE) {
            return decision(DialogueDecision.Outcome.TERMINAL, DialogueDecision.ReasonCode.SERVICE_UNAVAILABLE,
                    state, completion, null, null, null, newlyAchieved, SpeakingSession.Status.INTERRUPTED, trigger);
        }
        DialogueDecision.ReasonCode reason = switch (trigger) {
            case USER_END -> DialogueDecision.ReasonCode.USER_END_ACTION;
            case FINALIZED_TURN_LIMIT -> DialogueDecision.ReasonCode.FINALIZED_TURN_LIMIT;
            case SESSION_AGE -> DialogueDecision.ReasonCode.SESSION_AGE_LIMIT;
            case DAILY_QUOTA -> DialogueDecision.ReasonCode.DAILY_QUOTA_EXHAUSTED;
            case RECOVERY_EXPIRED -> DialogueDecision.ReasonCode.RECOVERY_EXPIRED;
            case SERVICE_RESTART -> DialogueDecision.ReasonCode.SERVICE_RESTART;
            case NONE, SERVICE_UNAVAILABLE -> DialogueDecision.ReasonCode.NORMAL;
        };
        SpeakingSession.Status status = completion.completionEligible()
                ? SpeakingSession.Status.COMPLETED : SpeakingSession.Status.INCOMPLETE;
        return decision(DialogueDecision.Outcome.TERMINAL, reason, state, completion, null, null, null,
                newlyAchieved, status, trigger);
    }

    private DialogueDecision.SystemTrigger effectiveClosingTrigger(ConversationStateView state,
            DialogueDecision.SystemTrigger explicitTrigger) {
        if (explicitTrigger != null && explicitTrigger != DialogueDecision.SystemTrigger.NONE) {
            return explicitTrigger;
        }
        if (state.closingStartedAt() == null) {
            return DialogueDecision.SystemTrigger.NONE;
        }
        if (state.closingReason() == null) {
            return DialogueDecision.SystemTrigger.RECOVERY_EXPIRED;
        }
        return switch (state.closingReason()) {
            case LEARNER_ENDED -> DialogueDecision.SystemTrigger.USER_END;
            case TURN_LIMIT -> DialogueDecision.SystemTrigger.FINALIZED_TURN_LIMIT;
            case SESSION_AGE -> DialogueDecision.SystemTrigger.SESSION_AGE;
            case DAILY_QUOTA -> DialogueDecision.SystemTrigger.DAILY_QUOTA;
            case RECOVERY_EXPIRED -> DialogueDecision.SystemTrigger.RECOVERY_EXPIRED;
            case SERVICE_RESTART -> DialogueDecision.SystemTrigger.SERVICE_RESTART;
            default -> DialogueDecision.SystemTrigger.RECOVERY_EXPIRED;
        };
    }

    private DialogueDecision decision(DialogueDecision.Outcome outcome, DialogueDecision.ReasonCode reason,
            ConversationStateView state, ConversationStateView.Completion completion, DialogueAct act,
            String reply, List<String> newlyAchieved, SpeakingSession.Status resultingStatus,
            DialogueDecision.SystemTrigger trigger) {
        return decision(outcome, reason, state, completion, act, reply, null, newlyAchieved, resultingStatus, trigger);
    }

    private DialogueDecision decision(DialogueDecision.Outcome outcome, DialogueDecision.ReasonCode reason,
            ConversationStateView state, ConversationStateView.Completion completion, DialogueAct act,
            String reply, String targetGoalId, List<String> newlyAchieved, SpeakingSession.Status resultingStatus,
            DialogueDecision.SystemTrigger trigger) {
        return new DialogueDecision(outcome, reason, act, reply, targetGoalId, trigger, resultingStatus,
                state.finalizedTurnCount(), state.validTurnCount(), state.consecutiveEvaluationErrors(),
                newlyAchieved, completion.requiredMilestoneMet(), completion.completionEligible(),
                completion.allGoalsMet(), completion.autoEndEligible());
    }
}
