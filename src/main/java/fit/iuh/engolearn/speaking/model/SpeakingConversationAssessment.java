package fit.iuh.engolearn.speaking.model;

import java.util.Objects;

public record SpeakingConversationAssessment(
        ValidatedTurnProposal proposal,
        DialogueDecision decision
) {
    public SpeakingConversationAssessment {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(decision, "decision");
    }
}
