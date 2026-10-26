package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class LearningPathPracticeRepository {

    private final MongoTemplate mongoTemplate;

    public LearningPathPracticeRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean startDay(ObjectId pathId, ObjectId userId, int weekNo, int dayNo,
                            String expectedStatus, Date expectedNextRunAt, Date startedAt,
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
                .filterArray(Criteria.where("w.week_no").is(weekNo))
                .filterArray(Criteria.where("d.day_no").is(dayNo));
        return mongoTemplate.updateFirst(new Query(path), update, LearningPath.class)
                .getModifiedCount() == 1;
    }
}
