package fit.iuh.engolearn.speaking.model;

import java.util.List;

public record SpeakingContentSnapshot(
        String topicItemId,
        String topicItemVersionId,
        String title,
        String description,
        List<SpeakingGoal> goals,
        Integer goalPercentage,
        int minimumValidTurns,
        List<String> vocabulary,
        List<String> expressions,
        long configRevision,
        String completionPolicyVersion,
        String context,
        String learnerRole,
        String aiRole,
        SpeakingItemConfig.OpeningSide openingSide
) {
    public SpeakingContentSnapshot(String topicItemId, String topicItemVersionId, String title,
            String description, List<SpeakingGoal> goals, Integer goalPercentage,
            int minimumValidTurns, List<String> vocabulary, List<String> expressions, long configRevision,
            String completionPolicyVersion) {
        this(topicItemId, topicItemVersionId, title, description, goals, goalPercentage,
                minimumValidTurns, vocabulary, expressions, configRevision, completionPolicyVersion,
                null, null, null, null);
    }

    public SpeakingContentSnapshot(String topicItemId, String topicItemVersionId, String title,
            String description, List<SpeakingGoal> goals, Integer goalPercentage,
            int minimumValidTurns, List<String> vocabulary, List<String> expressions, long configRevision) {
        this(topicItemId, topicItemVersionId, title, description, goals, goalPercentage,
                minimumValidTurns, vocabulary, expressions, configRevision, null);
    }

    public boolean legacyRatioPolicy() {
        return completionPolicyVersion == null || "LEGACY_RATIO_V1".equals(completionPolicyVersion);
    }

    public int requiredGoalCount() {
        if (!legacyRatioPolicy()) return (int) goals.stream()
                .filter(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED).count();
        int percentage = goalPercentage == null ? 100 : goalPercentage;
        return (goals.size() * percentage + 99) / 100;
    }
}
