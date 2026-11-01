package fit.iuh.engolearn.speaking.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ConversationStateView(
        SpeakingSession.Status status,
        int finalizedTurnCount,
        int validTurnCount,
        int consecutiveEvaluationErrors,
        Set<String> achievedGoalIds,
        Instant closingStartedAt,
        SpeakingSession.EndReason closingReason,
        long controllerEpoch,
        Long revision
) {
    public ConversationStateView {
        achievedGoalIds = immutableIds(achievedGoalIds);
    }

    public static ConversationStateView from(SpeakingSession session) {
        return new ConversationStateView(
                session.getStatus(),
                session.getCommittedTurnCount(),
                session.getValidTurnCount(),
                session.getConsecutiveEvaluationFailures(),
                immutableIds(session.getAchievedGoalIds()),
                session.getClosingAt(),
                session.getClosingReason(),
                session.getControllerEpoch(),
                session.getRevision());
    }

    public ConversationStateView projected(int additionalValidTurns, Collection<String> additionalGoalIds) {
        Set<String> projectedGoals = new LinkedHashSet<>(achievedGoalIds);
        if (additionalGoalIds != null) projectedGoals.addAll(additionalGoalIds);
        return new ConversationStateView(status, finalizedTurnCount,
                validTurnCount + additionalValidTurns, consecutiveEvaluationErrors,
                projectedGoals, closingStartedAt, closingReason, controllerEpoch, revision);
    }

    public Completion completionFor(SpeakingContentSnapshot snapshot) {
        Set<String> required = ids(snapshot.goals().stream()
                .filter(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED)
                .map(SpeakingGoal::id).toList());
        Set<String> all = ids(snapshot.goals().stream().map(SpeakingGoal::id).toList());
        Set<String> missingRequired = new LinkedHashSet<>(required);
        missingRequired.removeAll(achievedGoalIds);
        Set<String> missingOptional = new LinkedHashSet<>(all);
        missingOptional.removeAll(required);
        missingOptional.removeAll(achievedGoalIds);
        boolean legacy = snapshot.legacyRatioPolicy();
        boolean requiredMilestoneMet = legacy
                ? achievedGoalIds.size() >= snapshot.requiredGoalCount()
                : missingRequired.isEmpty();
        boolean minValidTurnsMet = validTurnCount >= snapshot.minimumValidTurns();
        boolean completionEligible = requiredMilestoneMet && minValidTurnsMet;
        boolean allGoalsMet = all.stream().allMatch(achievedGoalIds::contains);
        boolean autoEndEligible = legacy ? completionEligible : completionEligible && allGoalsMet;
        return new Completion(requiredMilestoneMet, minValidTurnsMet, completionEligible,
                allGoalsMet, autoEndEligible, missingRequired, missingOptional);
    }

    private static Set<String> immutableIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    private static Set<String> ids(Collection<String> ids) {
        return new LinkedHashSet<>(ids == null ? List.of() : ids);
    }

    public record Completion(
            boolean requiredMilestoneMet,
            boolean minValidTurnsMet,
            boolean completionEligible,
            boolean allGoalsMet,
            boolean autoEndEligible,
            Set<String> missingRequiredGoalIds,
            Set<String> missingOptionalGoalIds
    ) {
        public Completion {
            missingRequiredGoalIds = immutableIds(missingRequiredGoalIds);
            missingOptionalGoalIds = immutableIds(missingOptionalGoalIds);
        }
    }
}
