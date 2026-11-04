package fit.iuh.engolearn.speaking.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ValidatedTurnProposal(
        ValidityOutcome validityOutcome,
        List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
        List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
        List<SpeakingAssessmentRecord.Correction> corrections,
        String hint,
        SpeakingAiDraftResponseV1.Dialogue dialogue,
        SpeakingAiDraftResponseV1.UserIntent userIntent,
        List<String> validationReasons,
        String hintExampleEn,
        String hintLeadVi,
        String criteriaVersion
) {
    public enum ValidityOutcome { VALID, INVALID, EVALUATION_FAILED }

    public ValidatedTurnProposal(boolean valid,
            List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
            List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
            List<SpeakingAssessmentRecord.Correction> corrections,
            String hint,
            SpeakingAiDraftResponseV1.Dialogue dialogue) {
        this(valid ? ValidityOutcome.VALID : ValidityOutcome.INVALID, goalEvidence, resourceEvidence,
                corrections, hint, dialogue, SpeakingAiDraftResponseV1.UserIntent.UNKNOWN, List.of(), null, null,
                "speaking-v1");
    }

    public ValidatedTurnProposal(ValidityOutcome validityOutcome,
            List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
            List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
            List<SpeakingAssessmentRecord.Correction> corrections,
            String hint, SpeakingAiDraftResponseV1.Dialogue dialogue,
            SpeakingAiDraftResponseV1.UserIntent userIntent, List<String> validationReasons) {
        this(validityOutcome, goalEvidence, resourceEvidence, corrections, hint, dialogue, userIntent,
                validationReasons, null, null, "speaking-v1");
    }

    public ValidatedTurnProposal {
        goalEvidence = goalEvidence == null ? List.of() : List.copyOf(goalEvidence);
        resourceEvidence = resourceEvidence == null ? List.of() : List.copyOf(resourceEvidence);
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
        validationReasons = validationReasons == null ? List.of() : List.copyOf(validationReasons);
        userIntent = userIntent == null ? SpeakingAiDraftResponseV1.UserIntent.UNKNOWN : userIntent;
    }

    public boolean valid() {
        return validityOutcome == ValidityOutcome.VALID;
    }

    public static ValidatedTurnProposal invalid() {
        return new ValidatedTurnProposal(ValidityOutcome.INVALID, List.of(), List.of(), List.of(), null, null,
                SpeakingAiDraftResponseV1.UserIntent.UNKNOWN, List.of("INVALID_TURN"), null, null, "speaking-v1");
    }

    public static ValidatedTurnProposal evaluationFailed() {
        return new ValidatedTurnProposal(ValidityOutcome.EVALUATION_FAILED, List.of(), List.of(), List.of(), null,
                null, SpeakingAiDraftResponseV1.UserIntent.UNKNOWN, List.of("EVALUATION_FAILED"), null, null,
                "speaking-v1");
    }

    public static ValidatedTurnProposal valid(SpeakingAiDraftResponseV1.UserIntent intent,
            List<String> goalIds, SpeakingAiDraftResponseV1.Dialogue dialogue) {
        List<SpeakingAssessmentRecord.GoalEvidence> evidence = goalIds == null ? List.of() : goalIds.stream()
                .map(id -> new SpeakingAssessmentRecord.GoalEvidence(id, "validated"))
                .toList();
        return new ValidatedTurnProposal(ValidityOutcome.VALID, evidence, List.of(), List.of(), null, dialogue,
                intent, List.of(), null, null, "speaking-v1");
    }
}
