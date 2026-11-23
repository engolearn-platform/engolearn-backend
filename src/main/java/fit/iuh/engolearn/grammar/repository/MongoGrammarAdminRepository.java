package fit.iuh.engolearn.grammar.repository;

import com.mongodb.MongoException;
import fit.iuh.engolearn.grammar.model.GrammarAuditEntry;
import fit.iuh.engolearn.grammar.model.GrammarCounter;
import fit.iuh.engolearn.grammar.model.GrammarPublishedRevision;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class MongoGrammarAdminRepository implements GrammarAdminRepository {
    private static final int MAX_TRANSACTION_ATTEMPTS = 5;
    private static final String ORDER_COUNTER = "topic-order-and-code";
    private final MongoTemplate mongo;
    private final GrammarTopicStorageRepository storage;
    private final ObjectProvider<MongoTransactionManager> transactionManagers;

    @Override
    public TopicPage search(String search, GrammarLevel level, String status, int page, int size) {
        var filters = new ArrayList<Criteria>();
        if (search != null && !search.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(search.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria().orOperator(Criteria.where("titleVi").regex(term),
                    Criteria.where("titleEn").regex(term), Criteria.where("code").regex(term)));
        }
        if (level != null) {
            filters.add(Criteria.where("level").is(level));
        }
        if (status != null) {
            filters.add(statusFilter(status));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = storage.count(query);
        query.with(Sort.by("level", "order", "code")).skip((long) page * size).limit(size);
        query.fields().exclude("lessons.draft").exclude("lessons.published");
        return new TopicPage(storage.find(query), total);
    }

    @Override
    public TopicCounts counts() {
        return new TopicCounts(count("PUBLISHED"), count("IN_PROGRESS"), count("DRAFT"), count("ARCHIVED"));
    }

    @Override
    public Optional<GrammarTopic> findTopic(String id) {
        return storage.find(Query.query(Criteria.where("id").is(id)).limit(1)).stream().findFirst();
    }

    @Override
    public List<GrammarTopic> activeLevelTopics(GrammarLevel level) {
        Query query = Query.query(Criteria.where("level").is(level).and("status").ne(GrammarStatus.ARCHIVED))
                .with(Sort.by("order"));
        query.fields().exclude("lessons");
        return storage.find(query);
    }

    @Override
    public <T> T transact(Supplier<T> action) {
        MongoTransactionManager manager = transactionManagers.getIfAvailable();
        if (manager == null) {
            throw new DataAccessResourceFailureException("Grammar administration requires MongoDB transactions");
        }
        TransactionTemplate transactions = new TransactionTemplate(manager);
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> action.get());
            } catch (RuntimeException failure) {
                if (!retryable(failure)) {
                    throw failure;
                }
                if (attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw new CannotAcquireLockException("Grammar transaction could not acquire a write lock", failure);
                }
            }
        }
    }

    @Override
    public long lockTopicOrder(boolean allocateCode) {
        // Taking the same counter write lock serializes inserts, reorders and cross-level moves.
        Update change = new Update().inc("orderingRevision", 1);
        if (allocateCode) {
            change.inc("topicSequence", 1);
        }
        try {
            GrammarCounter counter = mongo.findAndModify(Query.query(Criteria.where("id").is(ORDER_COUNTER)),
                    change, FindAndModifyOptions.options().upsert(true).returnNew(true), GrammarCounter.class);
            return counter.getTopicSequence();
        } catch (DuplicateKeyException concurrentInit) {
            throw new CannotAcquireLockException("Grammar ordering counter is being initialized", concurrentInit);
        }
    }

    @Override
    public void reorderTopic(GrammarTopic topic, int order, Instant now) {
        long matched = mongo.updateFirst(currentVersion(topic),
                new Update().set("order", order).set("updatedAt", now).inc("version", 1),
                GrammarTopic.class).getMatchedCount();
        if (matched == 0) {
            throw new OptimisticLockingFailureException("Grammar topic version was changed while reordering");
        }
    }

    @Override
    public void insertTopic(GrammarTopic topic) {
        storage.insert(topic);
    }

    @Override
    public void saveTopic(GrammarTopic topic) {
        storage.save(topic);
    }

    @Override
    public void deleteTopic(GrammarTopic topic) {
        storage.delete(topic);
    }

    @Override
    public void insertAudit(GrammarAuditEntry audit) {
        mongo.insert(audit);
    }

    @Override
    public void insertRevision(GrammarPublishedRevision revision) {
        mongo.insert(revision);
    }

    private long count(String status) {
        return storage.count(Query.query(statusFilter(status)));
    }

    private static Criteria statusFilter(String status) {
        return switch (status) {
            case "IN_PROGRESS", "DRAFT" -> {
                Criteria ready = Criteria.where("lessons").elemMatch(Criteria.where("ready").is(true)
                        .and("status").ne(GrammarStatus.ARCHIVED.name()));
                Criteria draft = Criteria.where("status").is(GrammarStatus.DRAFT);
                yield new Criteria().andOperator(draft,
                        status.equals("IN_PROGRESS") ? ready : new Criteria().norOperator(ready));
            }
            default -> Criteria.where("status").is(GrammarStatus.valueOf(status));
        };
    }

    private static Query currentVersion(GrammarTopic topic) {
        return Query.query(Criteria.where("id").is(topic.getId()).and("version").is(topic.getVersion()));
    }

    private static boolean retryable(Throwable failure) {
        if (failure instanceof CannotAcquireLockException) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoFailure
                    && (mongoFailure.hasErrorLabel("TransientTransactionError") || mongoFailure.getCode() == 112)) {
                return true;
            }
        }
        return false;
    }
}
