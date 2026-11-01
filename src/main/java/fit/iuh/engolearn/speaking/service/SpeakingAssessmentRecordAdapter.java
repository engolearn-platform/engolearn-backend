package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;

public final class SpeakingAssessmentRecordAdapter {
    private SpeakingAssessmentRecordAdapter() {}

    public static SpeakingAssessmentRecord toRecord(String sessionId, String learnerId, String transcript,
            ValidatedTurnProposal proposal) {
        return SpeakingAssessmentRecord.builder()
                .sessionId(sessionId)
                .learnerId(learnerId)
                .transcript(transcript)
                .criteriaVersion("speaking-v1")
                .goalEvidence(proposal.goalEvidence())
                .resourceEvidence(proposal.resourceEvidence())
                .corrections(proposal.corrections())
                .hint(proposal.hint())
                .feedback(java.util.List.of())
                .build();
    }
}
