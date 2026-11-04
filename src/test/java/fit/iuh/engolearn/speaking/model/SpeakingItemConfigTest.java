package fit.iuh.engolearn.speaking.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakingItemConfigTest {
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
