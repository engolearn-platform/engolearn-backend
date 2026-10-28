package fit.iuh.engolearn.speaking.model;

import java.util.List;

public record SpeakingContentSnapshot(
        String topicItemId,
        String topicItemVersionId,
        String title,
        String description,
        List<SpeakingGoal> goals,
        int goalPercentage,
        int minimumValidTurns,
        List<String> vocabulary,
        List<String> expressions,
        long configRevision
) {
    public int requiredGoalCount() {
        return (goals.size() * goalPercentage + 99) / 100;
    }
}
