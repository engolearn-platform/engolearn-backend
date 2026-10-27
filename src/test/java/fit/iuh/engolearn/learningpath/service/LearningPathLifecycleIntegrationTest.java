package fit.iuh.engolearn.learningpath.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.learningpath.repository.LearningPathLifecycleRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LearningPathLifecycleIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static MongoDBContainer mongo;
    private static String mongoUri;

    @BeforeAll
    static void connect() {
        mongoUri = System.getenv("LEARNING_PATH_TEST_MONGO_URI");
        if (mongoUri != null && !mongoUri.isBlank()) return;
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "MongoDB integration test needs Docker or LEARNING_PATH_TEST_MONGO_URI");
        mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble"));
        mongo.start();
        mongoUri = mongo.getReplicaSetUrl();
    }

    @AfterAll
    static void disconnect() {
        if (mongo != null) mongo.stop();
    }

    @Test
    void jobCancelsAtHistoricalInactivityDeadlineAndKeepsOtherFields() {
        withTemplate(template -> {
            ObjectId id = new ObjectId();
            template.getCollection("learning_paths").insertOne(path(id, "2026-10-01", false));
            LearningPathLifecycleScheduler scheduler = scheduler(template, "2026-10-23");

            scheduler.processScheduledDeadlines();
            scheduler.processScheduledDeadlines();

            Document actual = template.getCollection("learning_paths").find(eq("_id", id)).first();
            assertThat(actual).isNotNull();
            assertThat(actual.getString("status")).isEqualTo("CANCELLED");
            assertThat(actual.getBoolean("is_active")).isFalse();
            assertThat(actual.getDate("ended_at")).isEqualTo(Date.from(midnight("2026-10-16")));
            assertThat(actual.get("next_run_at")).isNull();
            assertThat(actual.getString("custom_marker")).isEqualTo("preserved");
            assertThat(actual.get("cancel", Document.class).getDate("at"))
                    .isEqualTo(Date.from(midnight("2026-10-16")));
            assertThat(week(actual, 0).getString("status")).isEqualTo("CLOSED");
            assertThat(week(actual, 1).getString("status")).isEqualTo("CLOSED");
            assertThat(week(actual, 2).getString("status")).isEqualTo("OPEN");
            assertThat(day(actual, 0).getString("close_reason")).isEqualTo("WEEK_CLOSED");
            assertThat(week(actual, 0).get("stats", Document.class)
                    .get("overall", Document.class).getDouble("completion_rate")).isZero();
        });
    }

    @Test
    void weekThreeCompletionWinsWhenCancellationIsDueAtSameMidnight() {
        withTemplate(template -> {
            ObjectId id = new ObjectId();
            template.getCollection("learning_paths").insertOne(path(id, "2026-10-07", true));

            scheduler(template, "2026-10-23").processScheduledDeadlines();

            Document actual = template.getCollection("learning_paths").find(eq("_id", id)).first();
            assertThat(actual).isNotNull();
            assertThat(actual.getString("status")).isEqualTo("COMPLETED");
            assertThat(actual.getDate("ended_at")).isEqualTo(Date.from(midnight("2026-10-22")));
            assertThat(actual.get("cancel")).isNull();
            assertThat(week(actual, 2).getString("status")).isEqualTo("CLOSED");
            assertThat(actual.get("summary", Document.class)
                    .get("completion", Document.class).getInteger("answered")).isEqualTo(1);
        });
    }

    private void withTemplate(Consumer<MongoTemplate> assertions) {
        try (MongoClient client = MongoClients.create(mongoUri)) {
            MongoTemplate template = template(client);
            try {
                assertions.accept(template);
            } finally {
                template.getDb().drop();
            }
        }
    }

    private MongoTemplate template(MongoClient client) {
        return new MongoTemplate(client, "learning_path_job_"
                + UUID.randomUUID().toString().replace("-", ""));
    }

    private LearningPathLifecycleScheduler scheduler(MongoTemplate template, String nowDate) {
        LearningPathLifecycleRepository repository = new LearningPathLifecycleRepository(template);
        LearningPathLifecycleService service = new LearningPathLifecycleService(
                repository, new LearningPathQuestionRepository(template));
        return new LearningPathLifecycleScheduler(service, Clock.fixed(midnight(nowDate), ZONE));
    }

    private Document path(ObjectId id, String anchorDate, boolean hasAnswer) {
        List<Document> weeks = new ArrayList<>();
        for (int weekNo = 1; weekNo <= 3; weekNo++) {
            List<Document> items = new ArrayList<>();
            for (int order = 1; order <= 10; order++) {
                Document item = new Document("order", order)
                        .append("question_id", "q-" + weekNo + "-" + order)
                        .append("micro_skill", "Reading for Gist")
                        .append("answer", weekNo == 1 && order == 1 && hasAnswer
                                ? new Document("selected_option_id", "A")
                                        .append("is_correct", true)
                                        .append("answered_at", Date.from(midnight("2026-10-07")))
                                : null);
                items.add(item);
            }
            Document day = new Document("day_no", 1)
                    .append("status", weekNo == 1 && hasAnswer ? "IN_PROGRESS" : "NOT_STARTED")
                    .append("started_date", weekNo == 1 && hasAnswer ? "2026-10-07" : null)
                    .append("auto_close_at", weekNo == 1 && hasAnswer
                            ? Date.from(midnight("2026-10-08")) : null)
                    .append("progress", new Document("assigned", 10)
                            .append("answered", weekNo == 1 && hasAnswer ? 1 : 0)
                            .append("correct", weekNo == 1 && hasAnswer ? 1 : 0))
                    .append("items", items);
            Document week = new Document("week_no", weekNo)
                    .append("status", weekNo == 1 ? "OPEN" : "LOCKED")
                    .append("close_at", weekNo == 1 ? Date.from(midnight("2026-10-08")) : null)
                    .append("days", List.of(day));
            weeks.add(week);
        }
        return new Document("_id", id)
                .append("user_id", new ObjectId())
                .append("created_date", "2026-10-01")
                .append("status", hasAnswer ? "IN_PROGRESS" : "NOT_STARTED")
                .append("is_active", true)
                .append("current_week_no", 1)
                .append("micro_skills", List.of(new Document("micro_skill", "Reading for Gist")
                        .append("baseline", new Document("percent", 40.0))))
                .append("baseline_total", new Document("percent", 40.0))
                .append("progress", new Document("assigned", 30)
                        .append("answered", hasAnswer ? 1 : 0)
                        .append("correct", hasAnswer ? 1 : 0))
                .append("inactivity", new Document("anchor_date", anchorDate)
                        .append("cancel_at", Date.from(LocalDate.parse(anchorDate).plusDays(15)
                                .atStartOfDay(ZONE).toInstant())))
                .append("next_run_at", Date.from(midnight("2026-10-08")))
                .append("weeks", weeks)
                .append("custom_marker", "preserved");
    }

    private Document week(Document path, int index) {
        return path.getList("weeks", Document.class).get(index);
    }

    private Document day(Document path, int weekIndex) {
        return week(path, weekIndex).getList("days", Document.class).get(0);
    }

    private Instant midnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZONE).toInstant();
    }
}
