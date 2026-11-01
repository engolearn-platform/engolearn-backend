package fit.iuh.engolearn.speaking.model;

import java.util.List;

public record ValidatedTurnProposal(
        boolean valid,
        List<SpeakingAssessmentRecord.GoalEvidence> goalEvidence,
        List<SpeakingAssessmentRecord.ResourceEvidence> resourceEvidence,
        List<SpeakingAssessmentRecord.Correction> corrections,
        String hint,
        SpeakingAiDraftResponseV1.Dialogue dialogue
) {}
