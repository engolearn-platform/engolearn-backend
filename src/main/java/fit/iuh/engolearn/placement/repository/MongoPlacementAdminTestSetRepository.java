package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class MongoPlacementAdminTestSetRepository implements PlacementAdminTestSetRepository {
    private final MongoTemplate mongo;

    @Override
    public long countAll() {
        return mongo.count(new Query(), PlacementTestSet.class);
    }

    @Override
    public Page list(String search, PlacementTestSetStatus status, int page, int size) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        if (search != null && !search.isBlank()) {
            Pattern needle = Pattern.compile(Pattern.quote(search.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            query.addCriteria(new Criteria().orOperator(Criteria.where("code").regex(needle),
                    Criteria.where("name").regex(needle)));
        }
        long total = mongo.count(query, PlacementTestSet.class);
        query.with(Sort.by(Sort.Order.desc("created_at"), Sort.Order.asc("code"),
                        Sort.Order.desc("version"), Sort.Order.asc("_id")))
                .skip((long) page * size).limit(size);
        query.fields().include("code", "name", "version", "status", "created_at", "revision", "question_ids",
                "items.id", "items.order_index");
        return new Page(mongo.find(query, PlacementTestSet.class), total);
    }

    @Override
    public Optional<PlacementTestSet> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PlacementTestSet.class));
    }

    @Override
    public PlacementTestSet insert(PlacementTestSet set) {
        return mongo.insert(set);
    }

    @Override
    public int latestVersion(String code) {
        Query query = Query.query(Criteria.where("code").is(code)).with(Sort.by(Sort.Direction.DESC, "version"));
        query.fields().include("version");
        PlacementTestSet latest = mongo.findOne(query, PlacementTestSet.class);
        return latest == null ? 0 : latest.getVersion();
    }

    @Override
    public boolean hasEditableDraft(String code) {
        return mongo.exists(Query.query(Criteria.where("code").is(code).and("status").is(PlacementTestSetStatus.DRAFT)
                .and("question_ids").exists(true)), PlacementTestSet.class);
    }

    @Override
    public boolean hasAttempts(PlacementTestSet set) {
        return mongo.exists(Query.query(Criteria.where("test_set_id").is(set.getId())
                .and("test_set_version").is(set.getVersion())), PlacementAttempt.class);
    }

    @Override
    public boolean guardActiveQuestion(PlacementQuestion question) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(question.getId()).and("status").is(PlacementQuestionStatus.ACTIVE),
                revisionIs(question.getRevision())));
        // Writing the question joins the same conflict boundary as status and content edits.
        return mongo.updateFirst(query, new Update().inc("publication_guard", 1L), PlacementQuestion.class)
                .getMatchedCount() == 1;
    }

    @Override
    public Optional<PlacementTestSet> saveDraft(String id, long revision, String name, List<String> questionIds,
                                                Instant now) {
        return modify(id, PlacementTestSetStatus.DRAFT, revision,
                new Update().set("name", name).set("question_ids", questionIds), now);
    }

    @Override
    public Optional<PlacementTestSet> publish(String id, long revision, String name, List<String> questionIds,
                                              List<PlacementSnapshotItemVo> items, Instant now) {
        return modify(id, PlacementTestSetStatus.DRAFT, revision, new Update().set("name", name)
                .set("question_ids", questionIds).set("items", items).set("status", PlacementTestSetStatus.PUBLISHED), now);
    }

    @Override
    public Optional<PlacementTestSet> archive(String id, long revision, Instant now) {
        return modify(id, PlacementTestSetStatus.PUBLISHED, revision,
                new Update().set("status", PlacementTestSetStatus.ARCHIVED), now);
    }

    @Override
    public boolean deleteDraft(String id, long revision) {
        return mongo.remove(current(id, PlacementTestSetStatus.DRAFT, revision), PlacementTestSet.class)
                .getDeletedCount() == 1;
    }

    private Optional<PlacementTestSet> modify(String id, PlacementTestSetStatus status, long revision,
                                               Update update, Instant now) {
        update.inc("revision", 1L).set("updated_at", now);
        return Optional.ofNullable(mongo.findAndModify(current(id, status, revision), update,
                FindAndModifyOptions.options().returnNew(true), PlacementTestSet.class));
    }

    private static Query current(String id, PlacementTestSetStatus status, long revision) {
        return Query.query(new Criteria().andOperator(Criteria.where("_id").is(id).and("status").is(status),
                revisionIs(revision)));
    }

    private static Criteria revisionIs(long revision) {
        if (revision == 0) {
            return new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false));
        }
        return Criteria.where("revision").is(revision);
    }
}
