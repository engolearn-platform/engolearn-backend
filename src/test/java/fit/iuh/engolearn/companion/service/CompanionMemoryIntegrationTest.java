package fit.iuh.engolearn.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import fit.iuh.engolearn.companion.exception.CompanionMemoryException;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryResponse;
import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.CompanionMemorySettings;
import fit.iuh.engolearn.companion.model.MemoryOperationReceipt;
import fit.iuh.engolearn.companion.model.MemoryVectorCleanup;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.model.enums.MemorySource;
import fit.iuh.engolearn.companion.repository.CompanionMemoryRepository;
import fit.iuh.engolearn.companion.repository.MongoCompanionMemoryRepository;
import fit.iuh.engolearn.companion.service.impl.CompanionMemoryServiceImpl;
import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

@EnabledIfDockerAvailable
class CompanionMemoryIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String OWNER = "learner-one";
    private static final String OTHER = "learner-two";
    private CompanionMemoryRepository repository;
    private CompanionMemoryService service;

    @BeforeEach
    void setUpMemory() {
        repository = new MongoCompanionMemoryRepository(mongoTemplate);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mongoTransactionManager", new MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory()));
        service = new CompanionMemoryServiceImpl(repository, beans.getBeanProvider(MongoTransactionManager.class),
                mock(ApplicationEventPublisher.class));
    }

    @Test
    void absentSettingIsOnOwnerIsolatedAndOffOnlyFencesAutomaticWrites() {
        assertThat(service.settings(OWNER).automaticMemoryEnabled()).isTrue();
        assertThat(service.settingsSnapshot(OWNER)).isEqualTo(new MemorySettingsSnapshot(true, 0));
        MemoryOperationResult saved = service.mutate(create("turn-1", 0, "Remember I study English"), () -> { });
        assertThat(saved.memoryId()).matches("[0-9a-f-]{36}");
        assertThat(service.findCurrentByIds(OWNER, java.util.List.of(saved.memoryId()))).hasSize(1);
        assertThat(service.findCurrentByIds(OTHER, java.util.List.of(saved.memoryId()))).isEmpty();
        assertThatThrownBy(() -> service.update(OTHER, saved.memoryId(), "spoofed", 1))
                .isInstanceOfSatisfying(CompanionMemoryException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        service.updateSettings(OWNER, false);
        assertThat(service.settings(OWNER).automaticMemoryEnabled()).isFalse();
        MemoryMutation automatic = new MemoryMutation(OWNER, "conversation-1", "turn-2", 0, "fingerprint-2",
                MemoryMutation.Action.CREATED, null, MemoryCategory.GOAL, "Speak more fluently", MemorySource.INFERRED,
                MemoryRetrievalMode.RELEVANT, null, true, true, 0);
        assertThatThrownBy(() -> service.mutate(automatic, () -> { }))
                .isInstanceOfSatisfying(CompanionMemoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MEMORY_WRITE_FENCED"));
        MemoryMutation explicit = new MemoryMutation(OWNER, "conversation-1", "turn-3", 0, "explicit-key",
                MemoryMutation.Action.CREATED, null, MemoryCategory.GOAL, "Please remember my study goal", MemorySource.EXPLICIT,
                MemoryRetrievalMode.RELEVANT, null, false, false, 0);
        assertThat(service.mutate(explicit, () -> { }).memoryId()).isNotBlank();
        assertThat(service.findCurrentByIds(OWNER, java.util.List.of(saved.memoryId()))).hasSize(1);
    }

    @Test
    void repeatedOrdinalReplaysReceiptButChangedFingerprintConflicts() {
        MemoryMutation first = create("turn-retry", 3, "I prefer short lessons");
        MemoryOperationResult committed = service.mutate(first, () -> { });
        MemoryOperationResult replay = service.mutate(first, () -> { });
        assertThat(replay.operationId()).isEqualTo(committed.operationId());
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.memoryId()).isEqualTo(committed.memoryId());
        MemoryMutation changed = new MemoryMutation(OWNER, first.conversationId(), first.turnId(), first.ordinal(),
                "different-keyed-fingerprint", first.action(), null, first.category(), "I prefer very long lessons",
                first.source(), first.retrievalMode(), null, true, true, 0);
        assertThatThrownBy(() -> service.mutate(changed, () -> { }))
                .isInstanceOfSatisfying(CompanionMemoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MEMORY_OPERATION_CONFLICT"));
        assertThat(mongoTemplate.findAll(MemoryOperationReceipt.class)).hasSize(1);
    }

    @Test
    void deleteReceiptReplaysOnlyWhileCanonicalMemoryRemainsForgotten() {
        MemoryOperationResult created = service.mutate(create("turn-create", 0, "I am learning English"), () -> { });
        MemoryMutation deletion = new MemoryMutation(OWNER, "conversation-1", "turn-delete", 0, "forget-fingerprint",
                MemoryMutation.Action.DELETED, created.memoryId(), null, null, null, null, 1L, false, false, 0);
        MemoryOperationResult first = service.mutate(deletion, () -> { });
        MemoryOperationResult replay = service.mutate(deletion, () -> { });
        assertThat(first.action()).isEqualTo(MemoryMutation.Action.DELETED);
        assertThat(first.effectCurrent()).isTrue();
        assertThat(first.replayed()).isFalse();
        assertThat(replay.operationId()).isEqualTo(first.operationId());
        assertThat(replay.effectCurrent()).isTrue();
        assertThat(replay.replayed()).isTrue();
        assertThat(repository.findCurrent(OWNER, created.memoryId())).isEmpty();
    }

    @Test
    void concurrentEditsUseExpectedVersionCompareAndSet() throws Exception {
        String id = insert(OWNER, "Original").getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> one = pool.submit(() -> raceUpdate(id, "First", ready, start));
            Future<Object> two = pool.submit(() -> raceUpdate(id, "Second", ready, start));
            ready.await(); start.countDown();
            Object a = one.get(); Object b = two.get();
            assertThat(java.util.List.of(a, b).stream().filter(CompanionMemoryResponse.class::isInstance)).hasSize(1);
            assertThat(java.util.List.of(a, b).stream().filter(CompanionMemoryException.class::isInstance)
                    .map(CompanionMemoryException.class::cast).map(CompanionMemoryException::getCode))
                    .containsExactly("MEMORY_VERSION_CONFLICT");
            assertThat(repository.findCurrent(OWNER, id).orElseThrow().getVersion()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void forgetCommitsCanonicalDeleteWithContentFreeReceiptAndRollsBackTogetherOnFailure() {
        CompanionMemory record = insert(OWNER, "Forget this private detail");
        mongoTemplate.createCollection("companion_memory_vector_cleanup");
        mongoTemplate.executeCommand(new Document("collMod", "companion_memory_vector_cleanup")
                .append("validator", new Document("$jsonSchema", new Document("bsonType", "object")
                        .append("required", java.util.List.of("fieldThatCannotExist")))));
        try {
            assertThatThrownBy(() -> service.forget(OWNER, record.getId(), 1))
                    .isInstanceOfSatisfying(CompanionMemoryException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        } finally {
            mongoTemplate.executeCommand(new Document("collMod", "companion_memory_vector_cleanup").append("validator", new Document()));
        }
        assertThat(repository.findCurrent(OWNER, record.getId())).isPresent();
        assertThat(service.settingsSnapshot(OWNER).forgetRevision()).isZero();
        assertThat(mongoTemplate.findAll(MemoryVectorCleanup.class)).isEmpty();

        service.forget(OWNER, record.getId(), 1);
        assertThat(repository.findCurrent(OWNER, record.getId())).isEmpty();
        assertThat(service.settingsSnapshot(OWNER).forgetRevision()).isEqualTo(1);
        MemoryVectorCleanup receipt = mongoTemplate.findAll(MemoryVectorCleanup.class).getFirst();
        assertThat(receipt.getPointId()).isEqualTo(record.getId());
        assertThat(receipt.getRetryState()).isEqualTo("PENDING");
        assertThat(mongoTemplate.getCollection("companion_memory_vector_cleanup").find().first().keySet())
                .doesNotContain("content", "embedding", "source_turn_id");
    }

    @Test
    void oldAutomaticTurnCannotRecreateForgottenMemoryAndReplayDoesNotReportSuccess() {
        MemoryMutation original = create("turn-old", 0, "A durable preference");
        MemoryOperationResult first = service.mutate(original, () -> {});
        service.forget(OWNER, first.memoryId(), 1);
        MemoryOperationResult replay = service.mutate(original, () -> {});
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.effectCurrent()).isFalse();
        assertThatThrownBy(() -> service.mutate(create("another-old-turn", 0, original.content()), () -> {}))
                .isInstanceOfSatisfying(CompanionMemoryException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MEMORY_WRITE_FENCED"));
        assertThat(mongoTemplate.findAll(CompanionMemory.class)).isEmpty();
        assertThat(mongoTemplate.findAll(MemoryOperationReceipt.class)).hasSize(1);
    }

    @Test
    void foreignToolMutationsCannotUpdateOrDeleteCanonicalMemory() {
        CompanionMemory saved = insert(OWNER, "Owner preference");
        for (MemoryMutation.Action action : java.util.List.of(MemoryMutation.Action.UPDATED, MemoryMutation.Action.DELETED)) {
            MemoryMutation mutation = new MemoryMutation(OTHER, "foreign-conversation", "foreign-turn", 0,
                    "foreign-fingerprint", action, saved.getId(), null,
                    action == MemoryMutation.Action.UPDATED ? "Overwrite" : null, null, null, 1L, false, true, 0);
            assertThatThrownBy(() -> service.mutate(mutation, () -> {}))
                    .isInstanceOfSatisfying(CompanionMemoryException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(404));
        }
        assertThat(repository.findCurrent(OWNER, saved.getId()).orElseThrow().getContent()).isEqualTo("Owner preference");
        assertThat(mongoTemplate.findAll(MemoryOperationReceipt.class)).isEmpty();
        assertThat(mongoTemplate.findAll(MemoryVectorCleanup.class)).isEmpty();
    }

    private Object raceUpdate(String id, String content, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); start.await();
        try { return service.update(OWNER, id, content, 1); }
        catch (CompanionMemoryException failure) { return failure; }
    }

    private CompanionMemory insert(String owner, String content) {
        Instant now = Instant.now();
        return mongoTemplate.insert(CompanionMemory.builder().id(UUID.randomUUID().toString()).learnerId(owner)
                .category(MemoryCategory.FACT).content(content).source(MemorySource.EXPLICIT)
                .retrievalMode(MemoryRetrievalMode.RELEVANT).version(1).vectorSyncState("PENDING")
                .createdAt(now).updatedAt(now).build());
    }

    private MemoryMutation create(String turnId, int ordinal, String content) {
        return new MemoryMutation(OWNER, "conversation-1", turnId, ordinal, "keyed-" + turnId + ordinal,
                MemoryMutation.Action.CREATED, null, MemoryCategory.FACT, content, MemorySource.EXPLICIT,
                MemoryRetrievalMode.RELEVANT, null, true, true, 0);
    }
}
