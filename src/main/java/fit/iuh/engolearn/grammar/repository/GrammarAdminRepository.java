package fit.iuh.engolearn.grammar.repository;

import fit.iuh.engolearn.grammar.model.GrammarAuditEntry;
import fit.iuh.engolearn.grammar.model.GrammarPublishedRevision;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface GrammarAdminRepository {
    record TopicPage(List<GrammarTopic> topics, long total) { }

    record TopicCounts(long published, long inProgress, long draft, long archived) { }

    TopicPage search(String search, GrammarLevel level, String status, int page, int size);

    TopicCounts counts();

    Optional<GrammarTopic> findTopic(String id);

    List<GrammarTopic> activeLevelTopics(GrammarLevel level);

    <T> T transact(Supplier<T> action);

    long lockTopicOrder(boolean allocateCode);

    void reorderTopic(GrammarTopic topic, int order, Instant now);

    void insertTopic(GrammarTopic topic);

    void saveTopic(GrammarTopic topic);

    void deleteTopic(GrammarTopic topic);

    void insertAudit(GrammarAuditEntry audit);

    void insertRevision(GrammarPublishedRevision revision);
}
