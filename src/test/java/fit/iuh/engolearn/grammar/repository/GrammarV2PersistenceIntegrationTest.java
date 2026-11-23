package fit.iuh.engolearn.grammar.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrammarV2PersistenceIntegrationTest {
    private static final MongoDBContainer SERVER = new MongoDBContainer(DockerImageName.parse("mongo:8.0.29-noble")).withReplicaSet();
    private static MongoClient client;
    private AnnotationConfigApplicationContext context;
    private MongoTemplate mongo;
    private GrammarAdminRepository admin;
    private GrammarLearnerRepository learner;
    private String topicId;
    private String database;

    @BeforeAll static void start() {
        SERVER.start();
        client = MongoClients.create(SERVER.getReplicaSetUrl());
    }
    @AfterAll static void stop() {
        if (client != null) client.close();
        SERVER.stop();
    }
    @BeforeEach void setup() throws Exception {
        database = "grammar_v2_it_" + UUID.randomUUID().toString().replace("-", "");
        mongo = new MongoTemplate(client, database);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(MongoTemplate.class, () -> mongo);
        context.registerBean(MongoTransactionManager.class, () -> new MongoTransactionManager(mongo.getMongoDatabaseFactory()));
        context.scan("fit.iuh.engolearn.grammar.repository");
        context.refresh();
        admin = context.getBean(GrammarAdminRepository.class);
        learner = context.getBean(GrammarLearnerRepository.class);
        var bundle = readBundle("grammar-a1-present-simple.v2.importer.json");
        var collections = bundle.get("collections", Document.class);
        for (String name : collections.keySet()) {
            var documents = collections.getList(name, Document.class);
            mongo.createCollection(name);
            if (!documents.isEmpty()) mongo.getCollection(name).insertMany(documents);
        }
        topicId = collections.getList("grammar_topics", Document.class).getFirst().getString("_id");
    }
    @AfterEach void clean() {
        context.close();
        client.getDatabase(database).drop();
    }
    private static Document readBundle(String name) throws Exception {
        return Document.parse(Files.readString(Path.of("tools/mongo-json-importer/samples/grammar", name)));
    }
    private GrammarTopic full() {
        var topic = admin.findTopic(topicId).orElseThrow();
        assertThat(topic.getLessons()).hasSize(3);
        return topic;
    }
    @Test void realV2SeedHydratesAllThreeLessons() {
        var topic = full();
        assertThat(topic.getLessons().getFirst().getDraft().getBlocks()).hasSize(5);
        assertThat(topic.getLessons().getFirst().getDraft().getQuestions()).hasSize(5);
        assertThat(mongo.getCollection("grammar_topics").find().first()).doesNotContainKey("lessons");
    }
    @Test void childSaveUsesParentVersionAndRejectsStaleAggregate() {
        var changed = full();
        var stale = full();
        changed.getLessons().getFirst().getDraft().setTitleVi("Câu khẳng định đã biên tập");
        admin.transact(() -> { admin.saveTopic(changed); return null; });
        assertThat(full().getLessons().getFirst().getDraft().getTitleVi()).isEqualTo("Câu khẳng định đã biên tập");
        assertThat(full().getVersion()).isEqualTo(1L);
        assertThat(mongo.getCollection("grammar_topics").find().first()).doesNotContainKey("lessons");
        stale.getLessons().get(1).getDraft().setTitleVi("Thay đổi từ aggregate cũ");
        assertThatThrownBy(() -> admin.transact(() -> { admin.saveTopic(stale); return null; }))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(full().getLessons().get(1).getDraft().getTitleVi()).isNotEqualTo("Thay đổi từ aggregate cũ");
    }
    @Test void rollbackRestoresParentAndChild() {
        var topic = full();
        String before = topic.getLessons().getFirst().getDraft().getTitleVi();
        assertThatThrownBy(() -> admin.transact(() -> {
            topic.getLessons().getFirst().getDraft().setTitleVi("Không được commit");
            admin.saveTopic(topic);
            throw new IllegalStateException("Force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(full().getVersion()).isZero();
        assertThat(full().getLessons().getFirst().getDraft().getTitleVi()).isEqualTo(before);
    }
    @Test void legacyAndV2SearchAndCountsUseTheCorrectLessonSource() throws Exception {
        var legacy = readBundle("grammar-a1-present-simple.importer.json").get("collections", Document.class)
                .getList("grammar_topics", Document.class).getFirst();
        legacy.put("_id", "legacy-topic");
        legacy.put("code", "LEGACY-REVIEW");
        mongo.getCollection("grammar_topics").insertOne(legacy);
        var old = admin.findTopic("legacy-topic").orElseThrow();
        assertThat(old.getLessons()).hasSize(3);
        old.setTitleVi("Chủ đề legacy còn dùng được");
        admin.transact(() -> { admin.saveTopic(old); return null; });
        assertThat(mongo.getCollection("grammar_topics").find(new Document("_id", "legacy-topic")).first())
                .containsKey("lessons").doesNotContainKey("schema_version");
        var topic = full();
        topic.getLessons().getFirst().setReady(true);
        admin.transact(() -> { admin.saveTopic(topic); return null; });
        assertThat(admin.counts().inProgress()).isEqualTo(1);
        assertThat(admin.counts().draft()).isEqualTo(1);
        assertThat(admin.search(null, GrammarLevel.A1, "IN_PROGRESS", 0, 1).topics()).hasSize(1);
        assertThat(admin.search("Thì hiện tại đơn", null, null, 0, 1).total()).isEqualTo(1);
    }
    @Test void projectedReadCannotDeleteChildrenAndTopicDeleteCleansOnlyOwnedChildren() {
        full();
        var projected = admin.search(null, null, null, 0, 10).topics().getFirst();
        assertThatThrownBy(() -> admin.transact(() -> { admin.saveTopic(projected); return null; }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mongo.getCollection("grammar_lessons").countDocuments()).isEqualTo(3);
        admin.transact(() -> { admin.deleteTopic(full()); return null; });
        assertThat(mongo.getCollection("grammar_topics").countDocuments()).isZero();
        assertThat(mongo.getCollection("grammar_lessons").countDocuments()).isZero();
    }
    @Test void removingOneDraftLessonReconcilesOnlyThatChild() {
        var topic = full();
        String deletedId = topic.getLessons().removeLast().getId();
        admin.transact(() -> { admin.saveTopic(topic); return null; });
        assertThat(admin.findTopic(topicId).orElseThrow().getLessons()).hasSize(2);
        assertThat(mongo.getCollection("grammar_lessons").find(new Document("_id", deletedId)).first()).isNull();
    }
    @Test void learnerReadsPublishedChildrenAndFencesTheSameParent() {
        var topic = full();
        topic.setStatus(GrammarStatus.PUBLISHED);
        topic.getLessons().forEach(lesson -> {
            lesson.setStatus(GrammarStatus.PUBLISHED);
            lesson.setPublished(lesson.getDraft());
            lesson.setDraft(null);
            lesson.setContentVersion(1);
        });
        admin.transact(() -> { admin.saveTopic(topic); return null; });
        var theory = learner.findPublishedTopic(topicId).orElseThrow();
        assertThat(theory.getLessons()).hasSize(3);
        assertThat(theory.getLessons().getFirst().getPublished().getBlocks()).hasSize(5);
        assertThat(theory.getLessons().getFirst().getPublished().getQuestions()).isNull();
        assertThat(learner.findPublishedTopics()).hasSize(1);
        String lessonId = topic.getLessons().getFirst().getId();
        long editVersion = full().getVersion();
        assertThat(learner.transactForLearner("review-learner", () -> learner.fencePublishedLesson(topicId, lessonId))).isTrue();
        assertThat(full().getVersion()).isEqualTo(editVersion);
        assertThat(learner.transactForLearner("review-learner", () -> learner.fencePublishedLesson(topicId, "missing"))).isFalse();
    }
    @Test void archiveAndRestoreClearNullableOrderingFields() {
        var topic = full();
        topic.setStatus(GrammarStatus.ARCHIVED);
        topic.setOrderBeforeArchive(1);
        topic.setOrder(null);
        admin.transact(() -> { admin.saveTopic(topic); return null; });
        var archived = full();
        assertThat(archived.getOrder()).isNull();
        assertThat(archived.getOrderBeforeArchive()).isEqualTo(1);
        archived.setStatus(GrammarStatus.DRAFT);
        archived.setOrder(1);
        archived.setOrderBeforeArchive(null);
        admin.transact(() -> { admin.saveTopic(archived); return null; });
        assertThat(full().getOrder()).isEqualTo(1);
        assertThat(full().getOrderBeforeArchive()).isNull();
    }
    @Test void newlyCreatedTopicsUseV2WithoutEmbeddingEvenAnEmptyLessonArray() {
        var topic = GrammarTopic.builder().id(UUID.randomUUID().toString()).code("ENG-GRM-T0002")
                .titleVi("Chủ đề mới").titleEn("New topic").description("Nội dung nháp")
                .level(GrammarLevel.A1).status(GrammarStatus.DRAFT).order(2).lessons(new ArrayList<>()).build();
        admin.transact(() -> { admin.insertTopic(topic); return null; });
        var stored = mongo.getCollection("grammar_topics").find(new Document("_id", topic.getId())).first();
        assertThat(stored).containsEntry("schema_version", 2).doesNotContainKey("lessons");
        assertThat(admin.findTopic(topic.getId()).orElseThrow().getLessons()).isEmpty();
    }
}

