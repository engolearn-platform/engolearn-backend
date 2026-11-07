package fit.iuh.engolearn.speaking.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpeakingItemConfigTest {
    @Test
    void openingFieldsAreAvailableOnConfigAndPinnedSnapshot() {
        SpeakingItemConfig config = SpeakingItemConfig.builder()
                .context("A busy cafe")
                .learnerRole("waiter")
                .aiRole("customer")
                .openingSide(SpeakingItemConfig.OpeningSide.LEARNER)
                .build();

        SpeakingContentSnapshot snapshot = new SpeakingContentSnapshot("item", "version", "Cafe", "Order",
                List.of(), null, 3, List.of(), List.of(), 1, "REQUIRED_GOALS_V2",
                config.getContext(), config.getLearnerRole(), config.getAiRole(), config.getOpeningSide());
        config.setContext("Changed after session creation");
        config.setLearnerRole("changed learner");
        config.setAiRole("changed AI");
        config.setOpeningSide(SpeakingItemConfig.OpeningSide.AI);

        assertEquals("A busy cafe", snapshot.context());
        assertEquals("waiter", snapshot.learnerRole());
        assertEquals("customer", snapshot.aiRole());
        assertSame(SpeakingItemConfig.OpeningSide.LEARNER, snapshot.openingSide());
    }

    @Test
    void requiredGoalCountRoundsUpAcrossAllGoalsRegardlessOfPriority() {
        SpeakingItemConfig config = SpeakingItemConfig.builder()
                .goals(List.of(
                        new SpeakingGoal("one", "One", "", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("two", "Two", "", SpeakingGoal.Priority.OPTIONAL),
                        new SpeakingGoal("three", "Three", "", SpeakingGoal.Priority.OPTIONAL),
                        new SpeakingGoal("four", "Four", "", SpeakingGoal.Priority.OPTIONAL)))
                .goalPercentage(76)
                .minimumValidTurns(5)
                .build();

        assertEquals(4, config.requiredGoalCount());
        config.setGoalPercentage(75);
        assertEquals(3, config.requiredGoalCount());
    }
}
