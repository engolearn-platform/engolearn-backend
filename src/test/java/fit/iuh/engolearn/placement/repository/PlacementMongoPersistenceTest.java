package fit.iuh.engolearn.placement.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import fit.iuh.engolearn.placement.infra.PlacementMongoIndexConfig;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo;
import fit.iuh.engolearn.placement.service.PlacementTestSetSnapshotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementMongoPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static MongoClient client;
    private static MongoTemplate mongo;
    private static String databaseName;
    private static boolean mongoAvailable;

    @BeforeAll
    static void startMongo() {
        try {
            client = MongoClients.create("mongodb://localhost:27017/?serverSelectionTimeoutMS=1000");
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            databaseName = "engolearn_placement_it_" + UUID.randomUUID().toString().replace("-", "");
            mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, databaseName));
            mongoAvailable = true;
        } catch (RuntimeException exception) {
            mongoAvailable = false;
        }
    }

    @AfterAll
    static void stopMongo() {
        if (mongo != null) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mongoAvailable, "Local MongoDB is not available");
        mongo.getDb().drop();
        new PlacementMongoIndexConfig().placementMongoIndexes(mongo).afterPropertiesSet();
    }

    @Test
    void mapsM1SeedAndAssemblesAnImmutableTwentyQuestionSnapshot() throws Exception {
        seedQuestions();
        MongoPlacementQuestionRepository questions = new MongoPlacementQuestionRepository(mongo);

        PlacementQuestion listening = questions.findById("PLT-Q-A1-L-0001-V3").orElseThrow();
        assertThat(listening.getSkill()).isEqualTo(PlacementSkill.LISTENING);
        assertThat(listening.getStatus()).isEqualTo(PlacementQuestionStatus.DRAFT);
        assertThat(listening.getPassage()).isNull();
        assertThat(listening.getOptions()).hasSize(4);
        assertThat(listening.getProvenance().getAudioDurationSeconds()).isEqualTo(6.444);
        assertThat(questions.findById("PLT-Q-B2-L-0004-V3").orElseThrow()
                .getProvenance().getSemanticFamilyId()).isEqualTo("B2-R-4");

        List<String> previewIds = previewQuestionIds();
        List<PlacementQuestion> ordered = orderByIds(questions.findAllById(previewIds), previewIds);
        PlacementTestSet snapshot = PlacementTestSetSnapshotFactory.assemble(
                "placement-v3-preview", "PLT-V3-PREVIEW", "Placement V3 preview", 1,
                PlacementTestSetStatus.DRAFT, ordered);

        assertThat(snapshot.getItems()).hasSize(20);
        assertThat(snapshot.getItems().get(0).getPassage()).isEqualTo(
                "Lina comes from Spain. She now lives in Bristol.");
        assertThat(snapshot.getItems().get(14).getAudio().getUrl()).isNull();
        assertThat(snapshot.getItems().get(14).getAudio().getDurationSeconds()).isEqualTo(6.444);
        assertThat(snapshot.getItems()).allMatch(item -> item.getOptions().size() == 4);
    }

    @Test
    void mongoTestSetRepositoryUsesPublishedSetsAndOnlyUsesDraftWithDevOptIn() throws Exception {
        seedQuestions();
        MongoPlacementQuestionRepository questions = new MongoPlacementQuestionRepository(mongo);
        List<String> previewIds = previewQuestionIds();
        List<PlacementQuestion> ordered = orderByIds(questions.findAllById(previewIds), previewIds);
        PlacementTestSet draft = PlacementTestSetSnapshotFactory.assemble(
                "placement-draft", "PLT-DRAFT", "Draft", 1, PlacementTestSetStatus.DRAFT, ordered);
        mongo.insert(draft);

        MockEnvironment production = new MockEnvironment();
        MockEnvironment devNoOptIn = new MockEnvironment();
        devNoOptIn.setActiveProfiles("dev");
        MockEnvironment devOptIn = new MockEnvironment();
        devOptIn.setActiveProfiles("dev");

        assertThat(new MongoPlacementTestSetRepository(mongo, production, true).findPublished()).isEmpty();
        assertThat(new MongoPlacementTestSetRepository(mongo, devNoOptIn, false).findPublished()).isEmpty();
        assertThat(new MongoPlacementTestSetRepository(mongo, devOptIn, true).findPublished())
                .get().extracting(PlacementTestSet::getId).isEqualTo("placement-draft");

        ordered.forEach(question -> question.setStatus(PlacementQuestionStatus.ACTIVE));
        PlacementTestSet publishedOne = PlacementTestSetSnapshotFactory.assemble(
                "placement-published-1", "PLT-PUBLISHED-1", "Published one", 1,
                PlacementTestSetStatus.PUBLISHED, ordered);
        PlacementTestSet publishedTwo = PlacementTestSetSnapshotFactory.assemble(
                "placement-published-2", "PLT-PUBLISHED-2", "Published two", 2,
                PlacementTestSetStatus.PUBLISHED, ordered);
        mongo.insert(publishedOne);
        mongo.insert(publishedTwo);

        MongoPlacementTestSetRepository repository = new MongoPlacementTestSetRepository(mongo, production, false);
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            selected.add(repository.findPublished().orElseThrow().getId());
        }
        assertThat(selected).containsExactlyInAnyOrder("placement-published-1", "placement-published-2");
        PlacementTestSet version = repository.findVersion("placement-published-2", 2).orElseThrow();
        version.setName("mutated copy");
        assertThat(repository.findVersion("placement-published-2", 2).orElseThrow().getName())
                .isEqualTo("Published two");
    }

    @Test
    void concurrentStartsCreateOneGuestAttemptAndOneUserAttempt() throws Exception {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);

        List<PlacementStartSelection> guestStarts = race(
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), null, "guest-one"),
                        "guest-one", NOW),
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), null, "guest-one"),
                        "guest-one", NOW));
        assertThat(guestStarts).filteredOn(PlacementStartSelection::created).hasSize(1);
        assertThat(guestStarts).extracting(selection -> selection.attempt().getId()).containsOnly(
                guestStarts.get(0).attempt().getId());
        assertThat(mongo.count(Query.query(Criteria.where("guest_session_id").is("guest-one")),
                PlacementAttempt.class)).isEqualTo(1);

        List<PlacementStartSelection> userStarts = race(
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), "user-one", null),
                        null, NOW),
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), "user-one", null),
                        null, NOW));
        assertThat(userStarts).filteredOn(PlacementStartSelection::created).hasSize(1);
        assertThat(mongo.count(Query.query(Criteria.where("user_id").is("user-one")), PlacementAttempt.class))
                .isEqualTo(1);
    }

    @Test
    void startsForDifferentGuestsCreateIndependentActiveAttempts() throws Exception {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);

        List<PlacementStartSelection> starts = race(
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), null, "guest-one"),
                        "guest-one", NOW),
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), null, "guest-two"),
                        "guest-two", NOW));

        assertThat(starts).allMatch(PlacementStartSelection::created);
        assertThat(mongo.count(Query.query(Criteria.where("guest_session_id").in("guest-one", "guest-two")
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS)), PlacementAttempt.class)).isEqualTo(2);
    }

    @Test
    void twoGuestsCanFinishAndRemainSubmitted() {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);

        PlacementStartSelection first = repository.startIfEligible(
                activeAttempt(UUID.randomUUID().toString(), null, "guest-one"), "guest-one", NOW);
        PlacementStartSelection second = repository.startIfEligible(
                activeAttempt(UUID.randomUUID().toString(), null, "guest-two"), "guest-two", NOW);

        PlacementAttempt finishedFirst = repository.finish(first.attempt().getId(), first.attempt().getRevision(),
                PlacementAttemptStatus.SUBMITTED, NOW.plusSeconds(30), 1, PlacementLevel.A1,
                NOW.plus(Duration.ofHours(2))).orElseThrow();
        PlacementAttempt finishedSecond = repository.finish(second.attempt().getId(), second.attempt().getRevision(),
                PlacementAttemptStatus.SUBMITTED, NOW.plusSeconds(30), 1, PlacementLevel.A1,
                NOW.plus(Duration.ofHours(2))).orElseThrow();

        assertThat(finishedFirst.getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(finishedSecond.getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(repository.findClaimableForGuest("guest-one", NOW.plusSeconds(60))).isPresent();
        assertThat(repository.findClaimableForGuest("guest-two", NOW.plusSeconds(60))).isPresent();
    }

    @Test
    void userAndGuestCanRunActiveAttemptsIndependently() throws Exception {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);

        List<PlacementStartSelection> starts = race(
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), null, "guest-one"),
                        "guest-one", NOW),
                () -> repository.startIfEligible(activeAttempt(UUID.randomUUID().toString(), "user-one", null),
                        null, NOW));

        assertThat(starts).allMatch(PlacementStartSelection::created);
        assertThat(mongo.count(Query.query(Criteria.where("status").is(PlacementAttemptStatus.IN_PROGRESS)),
                PlacementAttempt.class)).isEqualTo(2);
    }

    @Test
    void userCannotStartSecondAttemptOrCreateSecondResultAndGuestClaimStillWorks() {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);

        PlacementStartSelection first = repository.startIfEligible(
                activeAttempt(UUID.randomUUID().toString(), "user-one", null), null, NOW);
        PlacementStartSelection second = repository.startIfEligible(
                activeAttempt(UUID.randomUUID().toString(), "user-one", null), null, NOW);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.attempt().getId()).isEqualTo(first.attempt().getId());
        assertThat(mongo.count(Query.query(Criteria.where("user_id").is("user-one")
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS)), PlacementAttempt.class)).isEqualTo(1);

        PlacementAttempt result = repository.finish(first.attempt().getId(), first.attempt().getRevision(),
                PlacementAttemptStatus.SUBMITTED, NOW.plusSeconds(30), 1, PlacementLevel.A1,
                NOW.plus(Duration.ofHours(2))).orElseThrow();
        PlacementStartSelection afterResult = repository.startIfEligible(
                activeAttempt(UUID.randomUUID().toString(), "user-one", null), null, NOW.plusSeconds(31));

        assertThat(afterResult.created()).isFalse();
        assertThat(afterResult.attempt().getId()).isEqualTo(result.getId());

        PlacementAttempt guest = submittedGuest(UUID.randomUUID().toString(), "guest-one");
        mongo.insert(guest);
        PlacementClaimSelection claim = repository.claimIfEligible(
                guest.getId(), guest.getRevision(), "guest-one", "user-one", NOW).orElseThrow();

        assertThat(claim.outcome()).isEqualTo(PlacementClaimOutcome.USER_HAS_RESULT);
        assertThat(claim.existingUserResult().getId()).isEqualTo(result.getId());
        assertThat(mongo.findById(guest.getId(), PlacementAttempt.class).getUserId()).isNull();
    }

    @Test
    void createsScopedUniqueIndexesForGuestAndUserAttempts() {
        Map<String, IndexInfo> indexes = new HashMap<>();
        mongo.indexOps(PlacementAttempt.class).getIndexInfo()
                .forEach(index -> indexes.put(index.getName(), index));

        IndexInfo guestIndex = indexes.get("one_active_guest_attempt");
        assertThat(guestIndex).isNotNull();
        assertThat(guestIndex.isUnique()).isTrue();
        assertThat(Document.parse(guestIndex.getPartialFilterExpression()))
                .isEqualTo(new Document("user_id", null).append("status", "IN_PROGRESS"));

        IndexInfo userIndex = indexes.get("one_blocking_user_attempt");
        assertThat(userIndex).isNotNull();
        assertThat(userIndex.isUnique()).isTrue();
        Document filter = Document.parse(userIndex.getPartialFilterExpression());
        assertThat(filter.get("user_id")).isEqualTo(new Document("$type", "string"));
        assertThat(filter.get("status")).isEqualTo(new Document("$in",
                List.of("IN_PROGRESS", "SUBMITTED", "AUTO_SUBMITTED")));
    }

    @Test
    void claimRejectsActiveUserAndThenFindsSubmittedUserResult() {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);
        PlacementAttempt guest = submittedGuest(UUID.randomUUID().toString(), "guest-one");
        mongo.insert(guest);
        PlacementAttempt active = activeAttempt(UUID.randomUUID().toString(), "user-one", null);
        mongo.insert(active);

        PlacementClaimSelection blocked = repository.claimIfEligible(
                guest.getId(), guest.getRevision(), "guest-one", "user-one", NOW).orElseThrow();
        assertThat(blocked.outcome()).isEqualTo(PlacementClaimOutcome.ACTIVE_ATTEMPT_EXISTS);
        assertThat(mongo.findById(guest.getId(), PlacementAttempt.class).getUserId()).isNull();

        PlacementAttempt finished = repository.finish(active.getId(), active.getRevision(),
                PlacementAttemptStatus.SUBMITTED, NOW, 1, PlacementLevel.A1, null).orElseThrow();
        PlacementClaimSelection result = repository.claimIfEligible(
                guest.getId(), guest.getRevision(), "guest-one", "user-one", NOW).orElseThrow();
        assertThat(result.outcome()).isEqualTo(PlacementClaimOutcome.USER_HAS_RESULT);
        assertThat(result.existingUserResult().getId()).isEqualTo(finished.getId());
        assertThat(mongo.findById(guest.getId(), PlacementAttempt.class).getUserId()).isNull();
    }

    @Test
    void simultaneousClaimsForDifferentUsersTransferOwnershipOnce() throws Exception {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);
        PlacementAttempt guest = submittedGuest(UUID.randomUUID().toString(), "guest-one");
        mongo.insert(guest);

        List<Optional<PlacementClaimSelection>> claims = race(
                () -> repository.claimIfEligible(guest.getId(), guest.getRevision(), "guest-one", "user-one", NOW),
                () -> repository.claimIfEligible(guest.getId(), guest.getRevision(), "guest-one", "user-two", NOW));

        assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
        assertThat(claims.stream().flatMap(Optional::stream).findFirst().orElseThrow().outcome())
                .isEqualTo(PlacementClaimOutcome.CLAIMED);
        PlacementAttempt stored = mongo.findById(guest.getId(), PlacementAttempt.class);
        assertThat(stored.getUserId()).isIn("user-one", "user-two");
        assertThat(stored.getRevision()).isEqualTo(1);
    }

    @Test
    void saveAnswerAndFinishAreCompareAndSetOperations() throws Exception {
        MongoPlacementAttemptRepository repository = new MongoPlacementAttemptRepository(mongo);
        PlacementAttempt active = activeAttempt(UUID.randomUUID().toString(), null, "guest-one");
        mongo.insert(active);

        List<Optional<PlacementAttempt>> saves = race(
                () -> repository.saveAnswer(active.getId(), 0, "q01", "a", NOW),
                () -> repository.saveAnswer(active.getId(), 0, "q01", "b", NOW));
        assertThat(saves).filteredOn(Optional::isPresent).hasSize(1);
        PlacementAttempt saved = mongo.findById(active.getId(), PlacementAttempt.class);
        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getAnswers()).hasSize(1);

        List<Optional<PlacementAttempt>> finishes = race(
                () -> repository.finish(active.getId(), 1, PlacementAttemptStatus.SUBMITTED,
                        NOW.plusSeconds(30), 1, PlacementLevel.A1, NOW.plus(Duration.ofHours(2))),
                () -> repository.finish(active.getId(), 1, PlacementAttemptStatus.AUTO_SUBMITTED,
                        NOW.plusSeconds(30), 1, PlacementLevel.A1, NOW.plus(Duration.ofHours(2))));
        assertThat(finishes).filteredOn(Optional::isPresent).hasSize(1);
        assertThat(mongo.findById(active.getId(), PlacementAttempt.class).getRevision()).isEqualTo(2);
    }

    private static void seedQuestions() throws Exception {
        String json = Files.readString(Path.of("data/placement/seed/placement_questions.mongo.json"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) MAPPER.readValue(json, List.class);
        List<Document> documents = raw.stream().map(Document::new).toList();
        MongoCollection<Document> collection = mongo.getCollection("placement_questions");
        collection.insertMany(documents);
    }

    private static List<String> previewQuestionIds() throws Exception {
        String json = Files.readString(Path.of("../data/bank-320/runtime-preview/test-sets.json"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sets = (List<Map<String, Object>>) MAPPER.readValue(json, List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) sets.get(0).get("items");
        return items.stream().map(item -> (String) item.get("questionId")).toList();
    }

    private static List<PlacementQuestion> orderByIds(List<PlacementQuestion> questions, List<String> ids) {
        Map<String, PlacementQuestion> byId = new HashMap<>();
        questions.forEach(question -> byId.put(question.getId(), question));
        return ids.stream().map(byId::get).toList();
    }

    private static PlacementAttempt activeAttempt(String id, String userId, String guestSessionId) {
        return PlacementAttempt.builder().id(id).userId(userId).guestSessionId(guestSessionId)
                .testSetId("placement-test").testSetVersion(1).status(PlacementAttemptStatus.IN_PROGRESS)
                .startedAt(NOW).deadlineAt(NOW.plus(Duration.ofMinutes(15))).answers(new ArrayList<>())
                .revision(0).createdAt(NOW).updatedAt(NOW).build();
    }

    private static PlacementAttempt submittedGuest(String id, String guestSessionId) {
        return PlacementAttempt.builder().id(id).guestSessionId(guestSessionId)
                .testSetId("placement-test").testSetVersion(1).status(PlacementAttemptStatus.SUBMITTED)
                .startedAt(NOW).deadlineAt(NOW.plus(Duration.ofMinutes(15))).endedAt(NOW)
                .claimExpiresAt(NOW.plus(Duration.ofHours(2))).answers(List.of(
                        new PlacementAnswerVo("q01", "a", NOW))).correctCount(1)
                .placementLevel(PlacementLevel.A1).revision(0).createdAt(NOW).updatedAt(NOW).build();
    }

    private static <T> List<T> race(java.util.concurrent.Callable<T> first,
                                    java.util.concurrent.Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<T>> futures = List.of(first, second).stream()
                    .map(operation -> executor.submit(() -> {
                        ready.countDown();
                        fire.await();
                        return operation.call();
                    })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get());
            return results;
        }
    }
}
