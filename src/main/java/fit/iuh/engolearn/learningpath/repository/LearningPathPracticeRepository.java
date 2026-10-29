package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDraft;
import java.util.Date;
import java.util.Map;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class LearningPathPracticeRepository {

    private final MongoTemplate mongoTemplate;

    public LearningPathPracticeRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean submit(ObjectId pathId, ObjectId userId, int weekNo, int dayNo,
                          Long revision, Date now, Map<String, Object> fields) {
        Criteria day = Criteria.where("day_no").is(dayNo).and("status").is("IN_PROGRESS")
                .and("auto_close_at").gt(now);
        Criteria week = Criteria.where("week_no").is(weekNo).and("status").is("OPEN")
                .and("close_at").gt(now).and("days").elemMatch(day);
        Criteria path = Criteria.where("_id").is(pathId).and("user_id").is(userId)
                .and("is_active").is(true).and("status").is("IN_PROGRESS")
                .and("revision").is(revision).and("next_run_at").gt(now)
                .and("inactivity.cancel_at").gt(now).and("weeks").elemMatch(week);
        Update update = new Update();
        fields.forEach(update::set);
        update.inc("revision", 1);
        return mongoTemplate.updateFirst(new Query(path), update, LearningPath.class).getModifiedCount() == 1;
    }

    public boolean startDay(ObjectId pathId, ObjectId userId, int weekNo, int dayNo,
                            String expectedStatus, Long revision, Date expectedNextRunAt, Date startedAt,
                            String startedDate, Date autoCloseAt, Date nextRunAt) {
        Criteria day = Criteria.where("day_no").is(dayNo).and("status").is("NOT_STARTED");
        Criteria week = Criteria.where("week_no").is(weekNo)
                .and("status").is("OPEN")
                .and("close_at").gt(startedAt)
                .and("days").elemMatch(day);
        Criteria path = Criteria.where("_id").is(pathId)
                .and("user_id").is(userId)
                .and("is_active").is(true)
                .and("status").is(expectedStatus)
                .and("revision").is(revision)
                .and("next_run_at").is(expectedNextRunAt)
                .and("inactivity.cancel_at").gt(startedAt)
                .and("weeks").elemMatch(week);
        Update update = new Update()
                .set("status", "IN_PROGRESS")
                .set("weeks.$[w].days.$[d].status", "IN_PROGRESS")
                .set("weeks.$[w].days.$[d].started_at", startedAt)
                .set("weeks.$[w].days.$[d].started_date", startedDate)
                .set("weeks.$[w].days.$[d].auto_close_at", autoCloseAt)
                .set("next_run_at", nextRunAt)
                .inc("revision", 1)
                .filterArray(Criteria.where("w.week_no").is(weekNo))
                .filterArray(Criteria.where("d.day_no").is(dayNo));
        return mongoTemplate.updateFirst(new Query(path), update, LearningPath.class)
                .getModifiedCount() == 1;
    }

    public boolean saveDraft(ObjectId pathId, ObjectId userId, int weekNo, int dayNo,
                             String questionId, Long revision, Date expectedNextRunAt, Date savedAt,
                             LearningPathDraft draft, String anchorDate,
                             Date cancelAt, Date nextRunAt) {
        Criteria item = Criteria.where("question_id").is(questionId).and("answer").is(null);
        Criteria day = Criteria.where("day_no").is(dayNo)
                .and("status").is("IN_PROGRESS")
                .and("auto_close_at").gt(savedAt)
                .and("items").elemMatch(item);
        Criteria week = Criteria.where("week_no").is(weekNo)
                .and("status").is("OPEN")
                .and("close_at").gt(savedAt)
                .and("days").elemMatch(day);
        Criteria path = Criteria.where("_id").is(pathId)
                .and("user_id").is(userId)
                .and("is_active").is(true)
                .and("status").is("IN_PROGRESS")
                .and("revision").is(revision)
                .and("next_run_at").is(expectedNextRunAt).gt(savedAt)
                .and("inactivity.cancel_at").gt(savedAt)
                .and("weeks").elemMatch(week);
        Update update = new Update()
                .set("weeks.$[w].days.$[d].items.$[i].draft", draft)
                .set("inactivity.anchor_date", anchorDate)
                .set("inactivity.cancel_at", cancelAt)
                .set("next_run_at", nextRunAt)
                .inc("revision", 1)
                .filterArray(Criteria.where("w.week_no").is(weekNo))
                .filterArray(Criteria.where("d.day_no").is(dayNo))
                .filterArray(Criteria.where("i.question_id").is(questionId)
                        .and("i.answer").is(null));
        return mongoTemplate.updateFirst(new Query(path), update, LearningPath.class)
                .getModifiedCount() == 1;
    }
}
