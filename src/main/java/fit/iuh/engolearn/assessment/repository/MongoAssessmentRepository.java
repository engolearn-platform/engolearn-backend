package fit.iuh.engolearn.assessment.repository;

import fit.iuh.engolearn.assessment.model.*;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@lombok.RequiredArgsConstructor
public class MongoAssessmentRepository implements AssessmentRepository {
    private final MongoTemplate mongo;
    public void ensureGate(String user) {
        mongo.upsert(Query.query(where("user_id").is(user)),new Update().setOnInsert("user_id",user).setOnInsert("revision",0),"assessment_gates");
    }
    public void lock(String user) {
        mongo.findAndModify(Query.query(where("user_id").is(user)),new Update().inc("revision",1),Document.class,"assessment_gates");
    }
    public AssessmentAttempt attempt(String id,String user) {
        return mongo.findOne(Query.query(where("_id").is(id).and("user_id").is(user)),AssessmentAttempt.class);
    }
    public AssessmentAttempt request(String user,String request) {
        return mongo.findOne(Query.query(where("user_id").is(user).and("request_id").is(request)),AssessmentAttempt.class);
    }
    public AssessmentAttempt active(String user) {
        return mongo.findOne(Query.query(where("user_id").is(user).and("status").is("ACTIVE")),AssessmentAttempt.class);
    }
    public List<AssessmentAttempt> history(String user,int page,int size,boolean includeDemonstration) {
        var criteria=where("user_id").is(user).and("status").ne("ACTIVE");
        if(!includeDemonstration) criteria=criteria.and("paper.demonstration").ne(true);
        var query=Query.query(criteria).with(Sort.by(Sort.Direction.DESC,"submitted_at","_id")).skip((long)page*size).limit(size);
        query.fields().include("status","submitted_at","paper.series","paper.version","correct_count","level");
        return mongo.find(query,AssessmentAttempt.class);
    }
    public List<AssessmentAttempt> valid(String user) {
        return mongo.find(Query.query(where("user_id").is(user).and("status").is("READY")).with(Sort.by(Sort.Direction.DESC,"submitted_at","_id")),AssessmentAttempt.class);
    }
    public List<AssessmentPaper> published() {
        return mongo.find(Query.query(where("published").is(true)),AssessmentPaper.class);
    }
    public List<AssessmentAttempt> due(Instant now) {
        return mongo.find(Query.query(where("status").is("ACTIVE").and("next_due_at").lte(now)).limit(100),AssessmentAttempt.class);
    }
    public AssessmentAttempt save(AssessmentAttempt a) {return mongo.save(a);}
    public AssessmentProfile profile(String user) {return mongo.findOne(Query.query(where("user_id").is(user)),AssessmentProfile.class);}
    public void saveProfile(AssessmentProfile profile) {mongo.save(profile);}
    public boolean revoked(String user,String source) {
        return mongo.exists(Query.query(where("user_id").is(user).and("source_id").is(source)),"assessment_grant_revocations");
    }
    public void revoke(String user,String source) {
        mongo.upsert(Query.query(where("user_id").is(user).and("source_id").is(source)),
                new Update().setOnInsert("user_id",user).setOnInsert("source_id",source),"assessment_grant_revocations");
        mongo.updateMulti(Query.query(where("user_id").is(user)),new Update().pull("analysis_grants",source),AssessmentAttempt.class);
    }
    public DevelopmentBaseline devBaseline(String user) {var d=mongo.getCollection("assessment_dev_baselines").find(new Document("_id",user)).first();return d==null?null:new DevelopmentBaseline(Boolean.TRUE.equals(d.getBoolean("valid")),d.getString("level"));}
}
