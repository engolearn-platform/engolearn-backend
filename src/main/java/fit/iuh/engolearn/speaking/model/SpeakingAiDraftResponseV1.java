package fit.iuh.engolearn.speaking.model;

import java.util.List;

public record SpeakingAiDraftResponseV1(
        String contractVersion,
        Assessment assessment,
        Dialogue dialogue
) {
    public enum TurnValidity { VALID, INVALID, UNCERTAIN }
    public enum UserIntent { QUESTION, REQUEST, STATEMENT, CHANGE_OF_MIND, CLOSING_UTTERANCE, OTHER, UNKNOWN }
    public enum ContinuationType { QUESTION, INVITATION, ACTION_PROMPT, CONFIRMATION, NONE }
    public enum ResourceKind { VOCABULARY, EXPRESSION }

    public record Assessment(
            TurnValidity turnValidityProposal,
            UserIntent userIntent,
            List<GoalEvidence> goalEvidence,
            List<ResourceEvidence> resourceEvidence,
            List<Correction> corrections,
            String hintVi
    ) {}

    public record GoalEvidence(String goalId, String quote) {}
    public record ResourceEvidence(String resourceId, ResourceKind resourceKind, String quote) {}
    public record Correction(String original, String suggestedEnglish, String explanationVi) {}
    public record Dialogue(
            DialogueAct proposedAct,
            String spokenReply,
            ContinuationType continuationType,
            String primaryQuestion,
            String targetGoalId
    ) {}
}
