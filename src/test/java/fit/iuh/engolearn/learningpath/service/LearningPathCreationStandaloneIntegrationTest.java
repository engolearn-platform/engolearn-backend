package fit.iuh.engolearn.learningpath.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.learningpath.repository.PeriodicTestReadRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathCreationStandaloneIntegrationTest {

    private static GenericContainer<?> mongo;

    @BeforeAll
    static void connect() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        mongo = new GenericContainer<>(DockerImageName.parse("mongo:8.0.29-noble"))
                .withExposedPorts(27017);
        mongo.start();
    }

    @AfterAll
    static void disconnect() {
        if (mongo != null) mongo.stop();
    }

    @Test
    void createsPathOnStandaloneMongoWithoutTransactionManager() {
        String mongoUri = "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017);
        try (MongoClient client = MongoClients.create(mongoUri)) {
            assertThat(client.getDatabase("admin").runCommand(new Document("hello", 1))
                    .getString("setName")).isNull();
            MongoTemplate template = new MongoTemplate(client, "learning_path_creation_"
                    + UUID.randomUUID().toString().replace("-", ""));
            try {
                ObjectId userId = new ObjectId();
                ObjectId attemptId = new ObjectId();
                seedPeriodicTest(template, userId, attemptId);
                seedQuestions(template);

                LearningPathRepository paths = mock(LearningPathRepository.class);
                when(paths.findBySourceAttemptId(attemptId)).thenAnswer(invocation ->
                        Optional.ofNullable(template.findOne(Query.query(
                                Criteria.where("source.attempt_id").is(attemptId)), LearningPath.class)));
                when(paths.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());
                when(paths.save(any(LearningPath.class))).thenAnswer(invocation ->
                        template.save(invocation.getArgument(0, LearningPath.class)));
                PeriodicTestReadRepository periodicTests = new PeriodicTestReadRepository(template);
                QuestionPoolRepository questionPool = new QuestionPoolRepository(template);
                LearningPathRecommendationService recommendations = new LearningPathRecommendationService(
                        periodicTests, paths, questionPool);
                LearningPathCreationService creation = new LearningPathCreationService(
                        recommendations, periodicTests, paths, questionPool, new LearningPlanGenerator());
                CreateLearningPathRequest request = new CreateLearningPathRequest(
                        userId.toHexString(), attemptId.toHexString(), List.of("Reading for Gist"));

                var first = creation.create(request);
                var repeated = creation.create(request);

                assertThat(first.created()).isTrue();
                assertThat(repeated.created()).isFalse();
                assertThat(repeated.pathId()).isEqualTo(first.pathId());
                LearningPath saved = template.findById(new ObjectId(first.pathId()), LearningPath.class);
                assertThat(saved).isNotNull();
                Document persisted = template.getCollection("learning_paths")
                        .find(new Document("_id", new ObjectId(first.pathId()))).first();
                assertThat(persisted).isNotNull();
                assertThat(persisted.get("_id")).isInstanceOf(ObjectId.class);
                assertThat(persisted.getDate("created_at").toInstant()).isEqualTo(saved.getCreatedAt());
                assertThat(saved.getWeeks()).hasSize(3);
                assertThat(saved.getWeeks()).allSatisfy(week -> {
                    assertThat(week.getDays()).hasSize(1);
                    assertThat(week.getDays().getFirst().getItems()).hasSize(10);
                });
                assertThat(template.getCollection("learning_path_questions")
                        .countDocuments(new Document("path_id", new ObjectId(saved.getId())))).isEqualTo(30L);
            } finally {
                template.getDb().drop();
            }
        }
    }

    private void seedPeriodicTest(MongoTemplate template, ObjectId userId, ObjectId attemptId) {
        template.getCollection("attempts").insertOne(new Document("_id", attemptId)
                .append("user_id", userId)
                .append("type", "PERIODIC")
                .append("status", "COMPLETED")
                .append("result", new Document("level", "A2")));
        List<String> skills = List.of("Reading for Gist", "Reading for Specific Information",
                "Reading for Detail & Relationship", "Vocabulary in Context",
                "Inference & Author's View");
        List<Document> results = new ArrayList<>();
        for (int index = 0; index < skills.size(); index++) {
            int percent = index == 0 ? 20 : 80;
            results.add(new Document("_id", new ObjectId())
                    .append("attempt_id", attemptId)
                    .append("user_id", userId)
                    .append("micro_skill", skills.get(index))
                    .append("skill", "READING")
                    .append("correct", percent / 10)
                    .append("total", 10)
                    .append("percent", percent)
                    .append("band", "A2")
                    .append("is_valid", true));
        }
        template.getCollection("micro_skill_results").insertMany(results);
    }

    private void seedQuestions(MongoTemplate template) {
        List<Document> questions = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            List<Document> options = List.of(
                    new Document("option_id", "A").append("text", "A"),
                    new Document("option_id", "B").append("text", "B"),
                    new Document("option_id", "C").append("text", "C"),
                    new Document("option_id", "D").append("text", "D"));
            questions.add(new Document("question_id", "reading-gist-" + index)
                    .append("micro_skill", "Reading for Gist")
                    .append("skill", "READING")
                    .append("level", "A2")
                    .append("status", "ACTIVE")
                    .append("prompt", "Question " + index)
                    .append("options", options)
                    .append("correct_option_id", "A")
                    .append("explanation", "Explanation"));
        }
        template.getCollection("question_pool").insertMany(questions);
    }
}
