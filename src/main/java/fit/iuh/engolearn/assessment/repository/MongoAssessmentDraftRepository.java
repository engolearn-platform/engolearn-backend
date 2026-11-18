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
                .set("revision", draft.getRevision()).currentDate("updated_at");
        return mongo.updateFirst(query, changes, AssessmentDraft.class).getMatchedCount() == 1;
    }
}
