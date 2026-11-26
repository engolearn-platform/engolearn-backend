package fit.iuh.engolearn.grammar.repository;

import fit.iuh.engolearn.grammar.model.GrammarLessonProgress;
import fit.iuh.engolearn.grammar.model.GrammarLevelUnlock;
import fit.iuh.engolearn.grammar.model.GrammarPracticeSession;
import fit.iuh.engolearn.grammar.model.GrammarPublishedRevision;
import fit.iuh.engolearn.grammar.model.GrammarTopic;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface GrammarLearnerRepository {
    <T> T transactForLearner(String userId, Supplier<T> action);

    List<GrammarTopic> findPublishedTopics();

    Optional<GrammarTopic> findPublishedTopic(String topicId);

    boolean fencePublishedLesson(String topicId, String lessonId);

    Optional<GrammarPublishedRevision> findRevision(String topicId, String lessonId, int contentVersion);

    List<GrammarLessonProgress> findProgress(String userId);

    List<GrammarLessonProgress> findTopicProgress(String userId, String topicId);

    Optional<GrammarLessonProgress> findLessonProgress(String userId, String lessonId);

    void saveProgress(GrammarLessonProgress progress);

    List<GrammarLevelUnlock> findUnlocks(String userId);

    void insertUnlock(GrammarLevelUnlock unlock);

    Optional<GrammarPracticeSession> findOwnedSession(String userId, String sessionId);

    Optional<GrammarPracticeSession> findActiveSession(String userId, String topicId, String lessonId);

    void saveSession(GrammarPracticeSession session);
}
