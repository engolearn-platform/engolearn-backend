package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import com.mongodb.client.model.CountOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class LearningPathCompanionReadRepository {
    private static final int TIMEOUT_MS = 1500;
    private static final int PATH_CAP = 100;
    private static final int DAY_CAP = 500;
    private final MongoTemplate mongo;

    public LearningPathCompanionReadRepository(MongoTemplate mongo) { this.mongo = mongo; }

    private Document activeFilter(String owner) {
        return new Document("user_id", owner).append("is_active", true)
                .append("status", new Document("$in", List.of("NOT_STARTED", "IN_PROGRESS")));
    }

    public List<LearningPath> active(String owner, Instant afterTime, String afterId, int limit) {
        Document filter = activeFilter(owner);
        if (afterId != null) {
            filter.append("$or", List.of(
                    new Document("created_at", new Document("$lt", Date.from(afterTime))),
                    new Document("created_at", Date.from(afterTime))
                            .append("_id", new Document("$lt", new ObjectId(afterId)))));
        }
        Document projection = new Document();
        for (String field : List.of("created_at", "level", "status", "is_active", "current_week_no",
                "micro_skills.micro_skill", "progress", "next_run_at", "inactivity.cancel_at",
                "config.questions_per_day", "weeks.week_no", "weeks.status", "weeks.close_at",
                "weeks.days.day_no", "weeks.days.status", "weeks.days.started_at",
                "weeks.days.auto_close_at", "weeks.days.items.order", "weeks.days.items.question_id",
                "weeks.days.items.micro_skill")) {
            projection.append(field, 1);
        }
        return mongo.getCollection("learning_paths").find(filter).projection(projection)
                .sort(new Document("created_at", -1).append("_id", -1)).limit(limit)
                .maxTime(TIMEOUT_MS, TimeUnit.MILLISECONDS).map(d -> mongo.getConverter().read(LearningPath.class, d))
                .into(new ArrayList<>());
    }

    public boolean activeAnchor(String owner, Instant time, String id) {
        return mongo.getCollection("learning_paths").find(activeFilter(owner)
                        .append("_id", new ObjectId(id)).append("created_at", Date.from(time)))
                .projection(new Document("_id", 1)).limit(1).maxTime(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .first() != null;
    }

    public long completed(String owner) {
        return mongo.getCollection("learning_paths").countDocuments(
                new Document("user_id", owner).append("status", "COMPLETED"),
                new CountOptions().maxTime(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public record ActivityRows(List<Document> days, boolean limited) {}

    public ActivityRows recent(String owner, Instant from, Instant to) {
        Document range = new Document("$gte", Date.from(from)).append("$lt", Date.from(to));
        Document candidate = new Document("user_id", owner).append("$or", List.of(
                new Document("weeks.days", new Document("$elemMatch", new Document("started_at", range))),
                new Document("weeks.days", new Document("$elemMatch", new Document("closed_at", range)))));
        List<Document> pipeline = List.of(
                new Document("$match", candidate),
                new Document("$limit", PATH_CAP + 1),
                new Document("$project", new Document("weeks.week_no", 1).append("weeks.days.day_no", 1)
                        .append("weeks.days.status", 1).append("weeks.days.started_at", 1)
                        .append("weeks.days.closed_at", 1).append("weeks.days.close_reason", 1)
                        .append("weeks.days.result.completion_rate", 1).append("weeks.days.result.practice_score", 1)),
                new Document("$facet", new Document("paths", List.of(new Document("$count", "count")))
                        .append("days", List.of(new Document("$unwind", "$weeks"),
                                new Document("$unwind", "$weeks.days"),
                                new Document("$limit", DAY_CAP + 1),
                                new Document("$project", new Document("week_no", "$weeks.week_no")
                                        .append("day", "$weeks.days"))))));
        Document result = mongo.getCollection("learning_paths").aggregate(pipeline)
                .maxTime(TIMEOUT_MS, TimeUnit.MILLISECONDS).first();
        if (result == null) throw new IllegalStateException("Missing activity aggregation result");
        List<Document> counts = result.getList("paths", Document.class);
        List<Document> days = result.getList("days", Document.class);
        boolean limited = (!counts.isEmpty() && counts.getFirst().getInteger("count") > PATH_CAP)
                || days.size() > DAY_CAP;
        return new ActivityRows(limited ? List.of() : List.copyOf(days), limited);
    }
}
