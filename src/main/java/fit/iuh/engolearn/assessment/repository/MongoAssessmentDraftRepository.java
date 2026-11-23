package fit.iuh.engolearn.assessment.repository;

import fit.iuh.engolearn.assessment.model.*;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class MongoAssessmentDraftRepository implements AssessmentDraftRepository {
    private final MongoTemplate mongo;
    public MongoAssessmentDraftRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public AssessmentDraft bySeries(String series) {
        return mongo.findById("draft:" + series, AssessmentDraft.class);
    }

    public List<AssessmentDraft> summaries(int page) {
        var query = new Query().with(Sort.by("series")).skip(25L * page).limit(25);
        query.fields().include("series", "name", "version");
        return mongo.find(query, AssessmentDraft.class);
    }

    public AssessmentPaper latestPaper(String series) {
        var query = Query.query(where("series").is(series)).with(Sort.by(Sort.Direction.DESC, "version"));
        return mongo.findOne(query, AssessmentPaper.class);
    }

    public AssessmentPaper sourcePaper(String id) { return mongo.findById(id, AssessmentPaper.class); }
    public void insert(AssessmentDraft draft) { mongo.insert(draft); }

    public boolean replace(String expectedRevision, AssessmentDraft draft) {
        var query = Query.query(where("_id").is(draft.getId()).and("revision").is(expectedRevision));
        var changes = new Update().set("name", draft.getName()).set("skills", draft.getSkills())
                .set("units", draft.getUnits()).set("source_audio_units", draft.getSourceAudioUnits())
                .set("revision", draft.getRevision()).unset("reviewed_revision").currentDate("updated_at");
        return mongo.updateFirst(query, changes, AssessmentDraft.class).getMatchedCount() == 1;
    }

    public boolean markReviewed(AssessmentDraft draft) {
        return mongo.updateFirst(sameRevision(draft),
                new Update().set("reviewed_revision", draft.getRevision()), AssessmentDraft.class).getMatchedCount() == 1;
    }

    public boolean delete(AssessmentDraft draft) {
        return mongo.remove(sameRevision(draft), AssessmentDraft.class).getDeletedCount() == 1;
    }

    public List<AssessmentPaper> papers(int page) {
        var query = new Query().with(Sort.by(Sort.Order.asc("series"), Sort.Order.desc("version")))
                .skip(25L * page).limit(25);
        query.fields().include("series", "name", "version", "published");
        return mongo.find(query, AssessmentPaper.class);
    }

    public AssessmentPaper paperVersion(String series, int version) {
        return mongo.findOne(Query.query(where("series").is(series).and("version").is(version)), AssessmentPaper.class);
    }

    public AssessmentPaper publishedFromRevision(String series, String revision) {
        return mongo.findOne(Query.query(where("series").is(series).and("published_draft_revision").is(revision)),
                AssessmentPaper.class);
    }

    public void deactivatePublished(String series) {
        mongo.updateMulti(Query.query(where("series").is(series).and("published").is(true)),
                new Update().set("published", false), AssessmentPaper.class);
    }

    public void deactivatePaper(String id) {
        mongo.updateFirst(Query.query(where("_id").is(id)), new Update().set("published", false), AssessmentPaper.class);
    }

    public void insertPaper(AssessmentPaper paper) {
        mongo.insert(paper);
    }

    private Query sameRevision(AssessmentDraft draft) {
        return Query.query(where("_id").is(draft.getId()).and("revision").is(draft.getRevision()));
    }
}
