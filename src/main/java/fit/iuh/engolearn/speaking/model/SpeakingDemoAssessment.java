package fit.iuh.engolearn.speaking.model;

import java.util.Objects;

public record SpeakingDemoAssessment(
        ValidatedTurnProposal proposal,
        DialogueDecision decision
) {
    public SpeakingDemoAssessment {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(decision, "decision");
    }
}
