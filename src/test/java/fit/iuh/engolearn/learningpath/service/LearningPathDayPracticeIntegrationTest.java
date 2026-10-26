package fit.iuh.engolearn.learningpath.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.repository.LearningPathPracticeRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathDayPracticeIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-09T03:00:00Z");
    private static final Date AUTO_CLOSE_AT = Date.from(Instant.parse("2026-10-09T17:00:00Z"));
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
    void firstStartPersistsDeadlineAndRepeatKeepsIt() {
        try (MongoClient client = MongoClients.create(mongoUri)) {
            MongoTemplate template = new MongoTemplate(client, "learning_path_start_"
                    + UUID.randomUUID().toString().replace("-", ""));
            try {
                ObjectId pathId = new ObjectId();
                ObjectId userId = new ObjectId();
                insertPath(template, pathId, userId);
                insertSnapshots(template, pathId, userId);
                LearningPathRepository paths = mock(LearningPathRepository.class);
                when(paths.findByIdAndUserId(pathId, userId)).thenAnswer(invocation ->
                        Optional.ofNullable(template.findOne(Query.query(Criteria.where("_id").is(pathId)
                                .and("user_id").is(userId)), LearningPath.class)));
                LearningPathDayPracticeService service = new LearningPathDayPracticeService(
                        paths, new LearningPathPracticeRepository(template),
                        new LearningPathQuestionRepository(template), Clock.fixed(STARTED_AT, ZoneOffset.UTC));

                var first = service.start(pathId.toHexString(), 1, 1, userId.toHexString());
                var repeated = service.start(pathId.toHexString(), 1, 1, userId.toHexString());

                assertThat(first.questions()).hasSize(10);
                assertThat(repeated.startedAt()).isEqualTo(first.startedAt());
                assertThat(repeated.autoCloseAt()).isEqualTo(first.autoCloseAt());
                Document persisted = template.getCollection("learning_paths")
                        .find(new Document("_id", pathId)).first();
                assertThat(persisted).isNotNull();
                assertThat(persisted.getString("status")).isEqualTo("IN_PROGRESS");
                assertThat(persisted.getDate("next_run_at")).isEqualTo(AUTO_CLOSE_AT);
                Document day = persisted.getList("weeks", Document.class).getFirst()
                        .getList("days", Document.class).getFirst();
                assertThat(day.getString("status")).isEqualTo("IN_PROGRESS");
                assertThat(day.getString("started_date")).isEqualTo("2026-10-09");
                assertThat(day.getDate("started_at")).isEqualTo(Date.from(STARTED_AT));
                assertThat(day.getDate("auto_close_at")).isEqualTo(AUTO_CLOSE_AT);
            } finally {
                template.getDb().drop();
            }
        }
    }

    private void insertPath(MongoTemplate template, ObjectId pathId, ObjectId userId) {
        List<Document> items = new ArrayList<>();
        for (int order = 1; order <= 10; order++) {
            items.add(new Document("order", order)
                    .append("question_id", "q-" + order)
                    .append("micro_skill", "Reading for Gist")
                    .append("answer", null));
        }
        Document day = new Document("day_no", 1)
                .append("status", "NOT_STARTED")
                .append("progress", new Document("assigned", 10).append("answered", 0))
                .append("items", items);
        Document week = new Document("week_no", 1)
                .append("status", "OPEN")
                .append("close_at", Date.from(Instant.parse("2026-10-15T17:00:00Z")))
                .append("days", List.of(day));
        Document path = new Document("_id", pathId)
                .append("user_id", userId)
                .append("status", "NOT_STARTED")
                .append("is_active", true)
                .append("config", new Document("questions_per_day", 10))
                .append("inactivity", new Document("cancel_at",
                        Date.from(Instant.parse("2026-10-23T17:00:00Z"))))
                .append("next_run_at", Date.from(Instant.parse("2026-10-15T17:00:00Z")))
                .append("weeks", List.of(week));
        template.getCollection("learning_paths").insertOne(path);
    }

    private void insertSnapshots(MongoTemplate template, ObjectId pathId, ObjectId userId) {
        List<Document> options = List.of(
                new Document("option_id", "A").append("text", "A"),
                new Document("option_id", "B").append("text", "B"),
                new Document("option_id", "C").append("text", "C"),
                new Document("option_id", "D").append("text", "D"));
        List<Document> snapshots = new ArrayList<>();
        for (int order = 1; order <= 10; order++) {
            snapshots.add(new Document("path_id", pathId)
                    .append("user_id", userId)
                    .append("question_id", "q-" + order)
                    .append("skill", "READING")
                    .append("micro_skill", "Reading for Gist")
                    .append("prompt", "Question " + order)
                    .append("options", options)
                    .append("correct_option_id", "A")
                    .append("explanation", "hidden"));
        }
        template.getCollection("learning_path_questions").insertMany(snapshots);
    }
}
