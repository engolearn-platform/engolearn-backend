package fit.iuh.engolearn.learningpath.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.learningpath.contract.dto.LearningPathPageQueryDto;
import fit.iuh.engolearn.learningpath.contract.enums.*;
import fit.iuh.engolearn.learningpath.contract.exception.LearningPathCompanionReadException;
import fit.iuh.engolearn.learningpath.repository.LearningPathCompanionReadRepository;
import fit.iuh.engolearn.learningpath.service.impl.LearningPathCompanionReadsImpl;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LearningPathCompanionReadsIntegrationTest {
    private static final Instant FROM = Instant.parse("2026-11-22T17:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-27T09:00:00Z");
    private static final String OWNER = "owner";
    private static MongoDBContainer mongo;
    private MongoClient client;
    private MongoTemplate template;
    private LearningPathCompanionReadsImpl reads;

    @BeforeAll static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Isolated Mongo requires Docker");
        mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble"));
        mongo.start();
    }

    @AfterAll static void stop() { if (mongo != null) mongo.stop(); }

    @BeforeEach void connect() {
        client = MongoClients.create(mongo.getReplicaSetUrl());
        template = new MongoTemplate(client, "path_companion_" + UUID.randomUUID().toString().replace("-", ""));
        reads = new LearningPathCompanionReadsImpl(new LearningPathCompanionReadRepository(template),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach void disconnect() {
        if (template != null) template.getDb().drop();
        if (client != null) client.close();
    }

    @Test void activeUsesOwnerAndStableTiesWithExplicitUnverifiedCandidatesAndNoWrites() {
        Document older = path(OWNER, "IN_PROGRESS", true, List.of(day(1, "NOT_STARTED"), day(2, "IN_PROGRESS")));
        Document newer = path(OWNER, "NOT_STARTED", true, List.of(day(1, "NOT_STARTED")));
        Document foreign = path("other", "IN_PROGRESS", true, List.of(day(1, "IN_PROGRESS")));
        Document expired = path(OWNER, "IN_PROGRESS", true, List.of(day(1, "NOT_STARTED")))
                .append("next_run_at", Date.from(NOW));
        Document terminal = path(OWNER, "COMPLETED", false, List.of(day(1, "COMPLETED")));
        insert(older, newer, foreign, expired, terminal);
        List<Document> before = stored();
        var page = reads.active(OWNER, NOW, new LearningPathPageQueryDto(1, null));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().pathId()).isEqualTo(id(expired));
        assertThat(page.items().getFirst().nextActions()).isEmpty();
        assertThat(page.items().getFirst().availability()).isEqualTo(LearningPathAvailability.NOT_AVAILABLE);
        assertThat(page.hasMore()).isTrue();
        assertThatThrownBy(() -> reads.active("other", NOW, new LearningPathPageQueryDto(1, page.nextCursor())))
                .isInstanceOf(LearningPathCompanionReadException.class);
        assertThatThrownBy(() -> reads.active(OWNER, NOW.plusSeconds(1), new LearningPathPageQueryDto(1, page.nextCursor())))
                .isInstanceOf(LearningPathCompanionReadException.class);
        var next = reads.active(OWNER, NOW, new LearningPathPageQueryDto(5, page.nextCursor()));
        assertThat(next.items()).extracting(r -> r.pathId()).containsExactly(id(newer), id(older));
        var actions = next.items().getLast().nextActions();
        assertThat(actions).extracting(a -> a.action()).containsExactly(LearningPathAction.RESUME, LearningPathAction.START);
        assertThat(actions).allMatch(a -> a.requiresAppRecheck() && a.availability() == LearningPathAvailability.UNKNOWN);
        assertThat(next.hasMore()).isFalse();
        assertThat(stored()).isEqualTo(before);
    }

    @Test void exactCountsAndHalfOpenRecordedActivityIncludingCancelledPaths() {
        Document start = day(1, "CLOSED").append("started_at", Date.from(FROM))
                .append("closed_at", Date.from(NOW.minusSeconds(1)))
                .append("close_reason", "AUTO_CLOSE").append("result",
                        new Document("completion_rate", 0.0).append("practice_score", null));
        Document boundary = day(2, "CLOSED").append("started_at", Date.from(FROM.minusMillis(1)))
                .append("closed_at", Date.from(NOW));
        Document cancelled = path(OWNER, "CANCELLED", false, List.of(start, boundary));
        Document completed = path(OWNER, "COMPLETED", false, List.of(day(1, "COMPLETED")));
        Document foreign = path("other", "COMPLETED", false, List.of(start));
        insert(cancelled, completed, foreign);
        List<Document> before = stored();
        assertThat(reads.counts(OWNER).completedPaths()).isEqualTo(1);
        assertThat(reads.counts("empty").completedPaths()).isZero();
        var first = reads.recent(OWNER, FROM, NOW, new LearningPathPageQueryDto(1, null));
        assertThat(first.items()).hasSize(1);
        var row = first.items().getFirst();
        assertThat(row.pathId()).isEqualTo(id(cancelled));
        assertThat(row.eventKind()).isEqualTo(LearningPathEventKind.DAY_CLOSED);
        assertThat(row.dayStatus()).isEqualTo("CLOSED");
        assertThat(row.completionRate()).isZero();
        assertThat(row.practiceScore()).isNull();
        var last = reads.recent(OWNER, FROM, NOW, new LearningPathPageQueryDto(5, first.nextCursor()));
        assertThat(last.items()).hasSize(1);
        assertThat(last.items().getFirst().occurredAt()).isEqualTo(FROM);
        assertThat(last.items().getFirst().eventKind()).isEqualTo(LearningPathEventKind.DAY_STARTED);
        assertThat(last.hasMore()).isFalse();
        assertThatThrownBy(() -> reads.recent("other", FROM, NOW, new LearningPathPageQueryDto(1, first.nextCursor())))
                .isInstanceOf(LearningPathCompanionReadException.class);
        assertThatThrownBy(() -> reads.recent(OWNER, FROM.plusMillis(1), NOW, new LearningPathPageQueryDto(1, first.nextCursor())))
                .isInstanceOf(LearningPathCompanionReadException.class);
        assertThat(stored()).isEqualTo(before);
    }

    @Test void capsNeverPretendToHaveCompleteActivity() {
        List<Document> paths = new ArrayList<>();
        for (int i = 0; i < 101; i++) paths.add(path(OWNER, "COMPLETED", false,
                List.of(day(1, "COMPLETED").append("closed_at", Date.from(FROM)))));
        template.getCollection("learning_paths").insertMany(paths);
        long started = System.nanoTime();
        var limited = reads.recent(OWNER, FROM, NOW, new LearningPathPageQueryDto(5, null));
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        System.out.println("101 candidate paths: elapsed ms=" + elapsed);
        assertThat(limited.coverage()).isEqualTo(LearningPathCoverage.LIMITED);
        assertThat(limited.items()).isEmpty();
        assertThat(limited.nextCursor()).isNull();
        assertThat(reads.counts(OWNER).completedPaths()).isEqualTo(101);
        List<Document> days = new ArrayList<>();
        for (int i = 1; i <= 501; i++) days.add(day(i, "COMPLETED").append("closed_at", Date.from(FROM)));
        insert(path("large", "COMPLETED", false, days));
        started = System.nanoTime();
        assertThat(reads.recent("large", FROM, NOW, new LearningPathPageQueryDto(5, null)).coverage())
                .isEqualTo(LearningPathCoverage.LIMITED);
        System.out.println("501 nested days: elapsed ms=" + (System.nanoTime() - started) / 1_000_000);
        assertThatThrownBy(() -> reads.counts(" ")).isInstanceOf(LearningPathCompanionReadException.class);
        assertThatThrownBy(() -> reads.active(OWNER, NOW, new LearningPathPageQueryDto(6, null)))
                .isInstanceOf(LearningPathCompanionReadException.class);
    }

    private static Document path(String owner, String status, boolean active, List<Document> days) {
        return new Document("_id", new ObjectId()).append("user_id", owner)
                .append("created_at", Date.from(FROM)).append("status", status).append("is_active", active)
                .append("level", "B1").append("current_week_no", 1)
                .append("next_run_at", Date.from(NOW.plusSeconds(60)))
                .append("inactivity", new Document("cancel_at", Date.from(NOW.plusSeconds(60))))
                .append("config", new Document("questions_per_day", 1))
                .append("micro_skills", List.of(new Document("micro_skill", "reading")))
                .append("weeks", List.of(new Document("week_no", 1).append("status", "OPEN")
                        .append("close_at", Date.from(NOW.plusSeconds(60))).append("days", days)));
    }

    private static Document day(int number, String status) {
        return new Document("day_no", number).append("status", status)
                .append("auto_close_at", Date.from(NOW.plusSeconds(60)))
                .append("items", List.of(new Document("order", 1).append("question_id", "question")
                        .append("micro_skill", "reading")));
    }

    private static String id(Document doc) { return doc.getObjectId("_id").toHexString(); }
    private void insert(Document... docs) { template.getCollection("learning_paths").insertMany(List.of(docs)); }
    private List<Document> stored() { return template.getCollection("learning_paths").find().into(new ArrayList<>()); }
}
