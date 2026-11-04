package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;

public final class SpeakingAssessmentRecordAdapter {
    private SpeakingAssessmentRecordAdapter() {}

    public static SpeakingAssessmentRecord toRecord(String sessionId, String learnerId, String transcript,
            ValidatedTurnProposal proposal) {
        return toRecord(sessionId, learnerId, transcript, proposal, true);
    }

    public static SpeakingAssessmentRecord toRecord(String sessionId, String learnerId, String transcript,
            ValidatedTurnProposal proposal, boolean allowHint) {
        return SpeakingAssessmentRecord.builder()
                .sessionId(sessionId)
                .learnerId(learnerId)
                .transcript(transcript)
                .criteriaVersion("speaking-v1")
                .goalEvidence(proposal.goalEvidence())
                .resourceEvidence(proposal.resourceEvidence())
                .corrections(proposal.corrections())
                .hint(allowHint ? proposal.hint() : null)
                .hintExampleEn(allowHint ? proposal.hintExampleEn() : null)
                .hintLeadVi(allowHint ? proposal.hintLeadVi() : null)
                .criteriaVersion(proposal.criteriaVersion())
                .feedback(java.util.List.of())
                .build();
    }
}
