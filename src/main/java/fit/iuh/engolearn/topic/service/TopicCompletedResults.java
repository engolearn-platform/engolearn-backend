package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class TopicCompletedResults {
    private TopicCompletedResults() {}

    static void overlay(Map<String, UserTopicItemProgress> progress, List<UserTopicItemAttempt> attempts) {
        attempts.stream().filter(attempt -> attempt.getStatus() == AttemptStatus.COMPLETED && attempt.getCompletionResult() != null)
                .sorted(Comparator.comparing(UserTopicItemAttempt::getCompletedAt).thenComparing(UserTopicItemAttempt::getId))
                .forEach(attempt -> {
                    UserTopicItemProgress previous = progress.get(attempt.getTopicItemId());
                    if (previous != null && previous.getCompletedAt() != null && previous.getCompletedAt().isAfter(attempt.getCompletedAt())) return;
                    progress.put(attempt.getTopicItemId(), UserTopicItemProgress.builder()
                            .generation(attempt.getGeneration()).userId(attempt.getUserId()).topicId(attempt.getTopicId()).topicItemId(attempt.getTopicItemId())
                            .status(ItemProgressStatus.COMPLETED).lastCompletedAttemptId(attempt.getId())
                            .completedTopicItemVersionId(attempt.getTopicItemVersionId()).completedAt(attempt.getCompletedAt())
                            .lastAccessedAt(attempt.getLastAccessedAt())
                            .firstTryCorrectCount(attempt.getCompletionResult().firstTryCorrectCount())
                            .totalQuizCount(attempt.getCompletionResult().totalQuizCount()).build());
                });
    }
}
