package fit.iuh.engolearn.speaking.model;

import java.util.List;
import java.util.Objects;

public record SpeakingAiRequestV1(
        String contractVersion,
        String promptVersion,
        PinnedSnapshot pinnedSnapshot,
        List<String> achievedGoalIds,
        List<HistoryTurn> recentHistory,
        List<PinnedResource> resources,
        String currentFinalizedTranscript
) {
    public SpeakingAiRequestV1 {
        requireNonBlank(contractVersion, "contractVersion");
        requireNonBlank(promptVersion, "promptVersion");
        Objects.requireNonNull(pinnedSnapshot, "pinnedSnapshot");
        achievedGoalIds = immutableStrings(achievedGoalIds, "achievedGoalIds");
        recentHistory = immutableList(recentHistory, "recentHistory");
        resources = immutableList(resources, "resources");
        requireNonBlank(currentFinalizedTranscript, "currentFinalizedTranscript");
    }

    private static List<String> immutableStrings(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        values.forEach(value -> requireNonBlank(value, name + " item"));
        return List.copyOf(values);
    }

    private static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        values.forEach(value -> Objects.requireNonNull(value, name + " item"));
        return List.copyOf(values);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
    }

    public record PinnedSnapshot(
            String topicItemId,
            String topicItemVersionId,
            String title,
            String description,
            List<PinnedGoal> goals,
            List<String> vocabulary,
            List<String> expressions,
            String context,
            String learnerRole,
            String aiRole
    ) {
        public PinnedSnapshot(String topicItemId, String topicItemVersionId, String title, String description,
                List<PinnedGoal> goals, List<String> vocabulary, List<String> expressions) {
            this(topicItemId, topicItemVersionId, title, description, goals, vocabulary, expressions,
                    null, null, null);
        }

        public PinnedSnapshot {
            requireNonBlank(topicItemId, "topicItemId");
            requireNonBlank(topicItemVersionId, "topicItemVersionId");
            requireNonBlank(title, "title");
            requireNonBlank(description, "description");
            goals = immutableList(goals, "goals");
            vocabulary = immutableStrings(vocabulary, "vocabulary");
            expressions = immutableStrings(expressions, "expressions");
        }
    }

    public record PinnedGoal(String id, String title, String description, SpeakingGoal.Priority priority) {
        public PinnedGoal {
            requireNonBlank(id, "goal id");
            requireNonBlank(title, "goal title");
            requireNonBlank(description, "goal description");
            Objects.requireNonNull(priority, "goal priority");
        }
    }

    public record HistoryTurn(Role role, String text) {
        public HistoryTurn {
            Objects.requireNonNull(role, "history role");
            requireNonBlank(text, "history text");
        }
    }

    public record PinnedResource(String id, String kind, String text) {
        public PinnedResource {
            requireNonBlank(id, "resource id");
            requireNonBlank(kind, "resource kind");
            requireNonBlank(text, "resource text");
        }
    }

    public enum Role { LEARNER, PARTNER }
}
