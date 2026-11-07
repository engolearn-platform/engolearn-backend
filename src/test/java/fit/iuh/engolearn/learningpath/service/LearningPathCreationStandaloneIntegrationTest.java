package fit.iuh.engolearn.learningpath.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.assessment.repository.MongoAssessmentRepository;
import fit.iuh.engolearn.assessment.service.impl.AssessmentContractsServiceImpl;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import fit.iuh.engolearn.learningpath.service.impl.LearningPathCreationServiceImpl;
import fit.iuh.engolearn.learningpath.service.impl.LearningPathRecommendationServiceImpl;
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
                String userId = new ObjectId().toHexString();
                String attemptId = "native-standalone-result";
                seedPeriodicTest(template, userId, attemptId);
                seedQuestions(template);

                LearningPathRepository paths = mock(LearningPathRepository.class);
                when(paths.findBySourceAssessmentAttemptId(attemptId)).thenAnswer(invocation ->
                        Optional.ofNullable(template.findOne(Query.query(
                                Criteria.where("source.assessment_attempt_id").is(attemptId)), LearningPath.class)));
                when(paths.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());
                when(paths.save(any(LearningPath.class))).thenAnswer(invocation ->
                        template.save(invocation.getArgument(0, LearningPath.class)));
                var periodicTests = new AssessmentContractsServiceImpl(new MongoAssessmentRepository(template),
                        mock(AssessmentTransactions.class), new org.springframework.mock.env.MockEnvironment());
                QuestionPoolRepository questionPool = new QuestionPoolRepository(template);
                LearningPathRecommendationService recommendations = new LearningPathRecommendationServiceImpl(
                        periodicTests, paths, questionPool);
                LearningPathCreationService creation = new LearningPathCreationServiceImpl(
                        recommendations, periodicTests, paths, questionPool);
                CreateLearningPathRequest request = new CreateLearningPathRequest(
                        attemptId, List.of("Reading for Gist"));

                var first = creation.create(request, userId);
                var repeated = creation.create(request, userId);

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

    private void seedPeriodicTest(MongoTemplate template, String userId, String attemptId) {
        List<Document> results = new ArrayList<>();
        for (String code : List.of("R02", "R01", "R04", "L01", "L03")) {
            int correct = code.equals("R02") ? 2 : 8;
            results.add(new Document("code", code).append("correct", correct).append("total", 10)
                    .append("percentage", correct * 10).append("band", correct < 5 ? "NEEDS_PRACTICE" : "MEETS_THRESHOLD"));
        }
        template.getCollection("assessment_attempts").insertOne(new Document("_id", attemptId)
                .append("user_id", userId).append("status", "READY").append("level", "A2")
                .append("submitted_at", java.util.Date.from(java.time.Instant.now()))
                .append("paper", new Document("paper_id", "native-paper").append("demonstration", false))
                .append("skill_results", results));
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
