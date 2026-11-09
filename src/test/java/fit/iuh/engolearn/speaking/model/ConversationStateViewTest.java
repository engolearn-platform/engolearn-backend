package fit.iuh.engolearn.speaking.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationStateViewTest {
    @Test
    void v2RequiredMilestoneIsCompletionEligibleButNotAutoEndWithOptionalGoalMissing() {
        SpeakingContentSnapshot snapshot = snapshot("REQUIRED_GOALS_V2", null,
                new SpeakingGoal("g1", "Required one", "one", SpeakingGoal.Priority.REQUIRED),
                new SpeakingGoal("g2", "Required two", "two", SpeakingGoal.Priority.REQUIRED),
                new SpeakingGoal("g3", "Optional", "three", SpeakingGoal.Priority.OPTIONAL));
        SpeakingSession session = session(snapshot, List.of("g1", "g2"), 5, 5, 0);

        ConversationStateView.Completion completion = ConversationStateView.from(session)
                .completionFor(snapshot);

        assertTrue(completion.requiredMilestoneMet());
        assertTrue(completion.completionEligible());
        assertFalse(completion.allGoalsMet());
        assertFalse(completion.autoEndEligible());
    }

    @Test
    void legacyRatioUsesPinnedRatioEvenWhenRequiredFlagsWouldNotBeMet() {
        SpeakingContentSnapshot snapshot = snapshot(null, 66,
                new SpeakingGoal("g1", "Required one", "one", SpeakingGoal.Priority.REQUIRED),
                new SpeakingGoal("g2", "Optional two", "two", SpeakingGoal.Priority.OPTIONAL),
                new SpeakingGoal("g3", "Required three", "three", SpeakingGoal.Priority.REQUIRED));
        SpeakingSession session = session(snapshot, List.of("g1", "g2"), 5, 5, 0);

        ConversationStateView.Completion completion = ConversationStateView.from(session)
                .completionFor(snapshot);

        assertEquals(2, snapshot.requiredGoalCount());
        assertTrue(completion.requiredMilestoneMet());
        assertTrue(completion.completionEligible());
        assertTrue(completion.autoEndEligible());
    }

    private static SpeakingContentSnapshot snapshot(String policy, Integer percentage, SpeakingGoal... goals) {
        return new SpeakingContentSnapshot("item", "version", "Title", "Description", List.of(goals),
                percentage, 5, List.of(), List.of(), 1L, policy);
    }

    private static SpeakingSession session(SpeakingContentSnapshot snapshot, List<String> achieved,
            int finalized, int valid, int errors) {
        return SpeakingSession.builder().id("session").content(snapshot).status(SpeakingSession.Status.ACTIVE)
                .achievedGoalIds(achieved).committedTurnCount(finalized).validTurnCount(valid)
                .consecutiveEvaluationFailures(errors).controllerEpoch(1L).build();
    }
}
