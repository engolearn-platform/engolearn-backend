package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.response.CompanionMemoryPageResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsUpdateResponse;
import fit.iuh.engolearn.companion.exception.CompanionMemoryException;
import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.CompanionMemorySettings;
import fit.iuh.engolearn.companion.model.MemoryVectorCleanup;
import fit.iuh.engolearn.companion.model.MemoryOperationReceipt;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.repository.CompanionMemoryRepository;
import fit.iuh.engolearn.companion.service.CompanionMemoryCommitted;
import fit.iuh.engolearn.companion.service.CompanionMemoryForgotten;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.MemoryCleanupTask;
import fit.iuh.engolearn.companion.service.MemoryVectorDocument;
import fit.iuh.engolearn.companion.service.MemoryMutation;
import fit.iuh.engolearn.companion.service.MemoryOperationResult;
import fit.iuh.engolearn.companion.service.MemorySettingsSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanionMemoryServiceImpl implements CompanionMemoryService {
    private final CompanionMemoryRepository memories;
    private final org.springframework.beans.factory.ObjectProvider<MongoTransactionManager> transactionManagers;
    private final ApplicationEventPublisher events;

    @Override public CompanionMemoryPageResponse list(String learnerId, MemoryCategory category, String before, int limit) {
        Instant timestamp = null;
        String id = null;
        if (before != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(before), StandardCharsets.UTF_8).split("\\|", 2);
                if (parts.length != 2 || parts[1].isBlank()) throw new IllegalArgumentException();
                timestamp = Instant.parse(parts[0]); id = parts[1];
            } catch (RuntimeException invalid) { throw invalid("Memory cursor is invalid."); }
        }
        var page = memories.list(learnerId, category, timestamp, id, limit);
        String next = page.nextCursor() == null ? null : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(page.nextCursor().getBytes(StandardCharsets.UTF_8));
        return new CompanionMemoryPageResponse(page.items().stream().map(CompanionMemoryServiceImpl::response).toList(), next);
    }

    @Override public CompanionMemoryResponse update(String learnerId, String memoryId, String content, long expectedVersion) {
        validateContent(content);
        owned(learnerId, memoryId);
        CompanionMemory updated = memories.update(learnerId, memoryId, expectedVersion, content.trim(), Instant.now())
                .orElseThrow(() -> conflictOrMissing(learnerId, memoryId));
        afterCommit(updated);
        return response(updated);
    }

    @Override public void forget(String learnerId, String memoryId, long expectedVersion) {
        transact(() -> {
            CompanionMemory existing = owned(learnerId, memoryId);
            if (existing.getVersion() != expectedVersion) throw conflict();
            Instant now = Instant.now();
            long deletionVersion = expectedVersion + 1;
            memories.advanceForgetRevision(learnerId, now);
            if (!memories.deleteCurrent(learnerId, memoryId, expectedVersion)) throw conflictOrMissing(learnerId, memoryId);
            memories.insertCleanup(MemoryVectorCleanup.builder().id(UUID.randomUUID().toString()).pointId(memoryId)
                    .learnerId(learnerId).deletionVersion(deletionVersion).deletedAt(now).retryState("PENDING").build());
            afterCommitDelete(memoryId, learnerId, deletionVersion);
            return null;
        });
    }

    @Override public CompanionMemorySettingsResponse settings(String learnerId) {
        CompanionMemorySettings settings = memories.findSettings(learnerId).orElse(null);
        return new CompanionMemorySettingsResponse(settings == null || settings.getAutomaticMemoryEnabled() == null
                || settings.getAutomaticMemoryEnabled());
    }

    @Override public CompanionMemorySettingsUpdateResponse updateSettings(String learnerId, boolean enabled) {
        return transact(() -> response(memories.setAutomaticMemoryEnabled(learnerId, enabled, Instant.now())));
    }

    @Override public MemorySettingsSnapshot settingsSnapshot(String learnerId) {
        CompanionMemorySettings settings = memories.findSettings(learnerId).orElse(null);
        return new MemorySettingsSnapshot(settings == null || settings.getAutomaticMemoryEnabled() == null
                || settings.getAutomaticMemoryEnabled(), settings == null ? 0 : settings.getForgetRevision());
    }

    @Override public MemoryOperationResult mutate(MemoryMutation mutation, Runnable conversationFence) {
        validateMutation(mutation);
        return transact(() -> {
            conversationFence.run();
            var existingReceipt = memories.findReceipt(mutation.conversationId(), mutation.turnId(), mutation.ordinal());
            if (existingReceipt.isPresent()) {
                MemoryOperationReceipt receipt = existingReceipt.get();
                if (!receipt.getFingerprint().equals(mutation.fingerprint()))
                    throw new CompanionMemoryException(409, "MEMORY_OPERATION_CONFLICT", "Memory operation does not match the committed turn operation.", false);
                boolean current = mutation.action() == MemoryMutation.Action.DELETED
                        ? memories.findCurrent(mutation.learnerId(), receipt.getMemoryId()).isEmpty()
                            && settingsSnapshot(mutation.learnerId()).forgetRevision() >= receipt.getForgetRevision()
                        : memories.findCurrent(mutation.learnerId(), receipt.getMemoryId())
                            .filter(memory -> memory.getVersion() == receipt.getMemoryVersion()).isPresent();
                return new MemoryOperationResult(receipt.getId(), MemoryMutation.Action.valueOf(receipt.getAction()),
                        receipt.getMemoryId(), receipt.getMemoryVersion(), current, true);
            }

            Instant now = Instant.now();
            boolean fencePassed;
            if (mutation.automatic()) {
                fencePassed = mutation.admissionAutomaticEnabled()
                        && memories.fenceAutomaticWrite(mutation.learnerId(), mutation.expectedForgetRevision(), now);
            } else {
                fencePassed = memories.fenceExplicitWrite(mutation.learnerId(), now);
            }
            if (!fencePassed) throw new CompanionMemoryException(409, "MEMORY_WRITE_FENCED", "Memory changed or automatic memory is disabled.", false);

            CompanionMemory memory;
            String memoryId = mutation.memoryId();
            long version;
            long forgetRevision = 0;
            switch (mutation.action()) {
                case CREATED -> {
                    validateContent(mutation.content());
                    String content = mutation.content().trim();
                    if (memories.findDuplicate(mutation.learnerId(), mutation.category(), content).isPresent())
                        throw new CompanionMemoryException(409, "MEMORY_DUPLICATE", "An equivalent memory already exists.", false);
                    memoryId = java.util.UUID.randomUUID().toString();
                    memory = CompanionMemory.builder().id(memoryId).learnerId(mutation.learnerId()).category(mutation.category())
                            .content(content).source(mutation.source()).retrievalMode(mutation.retrievalMode() == null
                                    ? MemoryRetrievalMode.RELEVANT : mutation.retrievalMode())
                            .sourceTurnId(mutation.turnId()).version(1).vectorSyncState("PENDING").createdAt(now).updatedAt(now).build();
                    memories.insert(memory); version = 1;
                }
                case UPDATED -> {
                    validateContent(mutation.content());
                    owned(mutation.learnerId(), requireMemoryId(mutation.memoryId()));
                    memory = memories.update(mutation.learnerId(), mutation.memoryId(), requireVersion(mutation.expectedVersion()),
                            mutation.content().trim(), now).orElseThrow(() -> conflictOrMissing(mutation.learnerId(), mutation.memoryId()));
                    version = memory.getVersion();
                }
                case DELETED -> {
                    memory = owned(mutation.learnerId(), requireMemoryId(mutation.memoryId()));
                    long expected = requireVersion(mutation.expectedVersion());
                    if (memory.getVersion() != expected) throw conflict();
                    forgetRevision = memories.advanceForgetRevision(mutation.learnerId(), now);
                    if (!memories.deleteCurrent(mutation.learnerId(), memory.getId(), expected))
                        throw conflictOrMissing(mutation.learnerId(), memory.getId());
                    version = expected + 1;
                    memories.insertCleanup(MemoryVectorCleanup.builder().id(java.util.UUID.randomUUID().toString())
                            .pointId(memory.getId()).learnerId(mutation.learnerId()).deletionVersion(version)
                            .deletedAt(now).retryState("PENDING").createdAt(now).updatedAt(now).build());
                    afterCommitDelete(memory.getId(), mutation.learnerId(), version);
                }
                default -> throw invalid("Memory action is invalid.");
            }
            String receiptId = MemoryOperationReceipt.idFor(mutation.conversationId(), mutation.turnId(), mutation.ordinal());
            memories.insertReceipt(MemoryOperationReceipt.builder().id(receiptId).conversationId(mutation.conversationId())
                    .turnId(mutation.turnId()).ordinal(mutation.ordinal()).fingerprint(mutation.fingerprint())
                    .action(mutation.action().name()).memoryId(memoryId).memoryVersion(version).resultCode("SUCCESS")
                    .forgetRevision(forgetRevision)
                    .committedAt(now).createdAt(now).updatedAt(now).build());
            if (mutation.action() != MemoryMutation.Action.DELETED) {
                CompanionMemory committed = memory;
                afterCommit(committed);
            }
            return new MemoryOperationResult(receiptId, mutation.action(), memoryId, version, true, false);
        });
    }

    @Override public List<MemoryVectorDocument> findCurrentByIds(String learnerId, Collection<String> ids) {
        return memories.findCurrentByLearnerIdAndIds(learnerId, ids).stream().map(CompanionMemoryServiceImpl::vectorDocument).toList();
    }
    @Override public java.util.Optional<MemoryVectorDocument> findCurrentByIdAndVersion(String memoryId, long version) {
        return memories.findCurrentByIdAndVersion(memoryId, version).map(CompanionMemoryServiceImpl::vectorDocument);
    }
    @Override public List<MemoryVectorDocument> listCurrent(String learnerId, MemoryRetrievalMode mode) {
        return memories.listCurrentByLearnerId(learnerId, mode).stream().map(CompanionMemoryServiceImpl::vectorDocument).toList();
    }
    @Override public List<MemoryVectorDocument> listCurrent(String learnerId, MemoryRetrievalMode mode, int limit) {
        return memories.listCurrentByLearnerId(learnerId, mode, limit).stream().map(CompanionMemoryServiceImpl::vectorDocument).toList();
    }
    @Override public List<MemoryVectorDocument> listCurrentForSync(int limit) {
        return memories.listCurrentForSync(limit).stream().map(CompanionMemoryServiceImpl::vectorDocument).toList();
    }
    @Override public List<MemoryCleanupTask> listCleanupTasks(int limit) {
        return memories.listVectorCleanups(limit).stream().map(row -> new MemoryCleanupTask(row.getPointId(), row.getLearnerId(), row.getDeletionVersion(), row.getDeletedAt())).toList();
    }
    @Override public List<MemoryCleanupTask> listPendingCleanupTasks(int limit) {
        return memories.listPendingVectorCleanups(limit).stream().map(row -> new MemoryCleanupTask(row.getPointId(), row.getLearnerId(), row.getDeletionVersion(), row.getDeletedAt())).toList();
    }
    @Override public List<MemoryCleanupTask> listCleanupTasksForReconciliation(String afterPointId, int limit) {
        return memories.listVectorCleanupsForReconciliation(afterPointId, limit).stream()
                .map(row -> new MemoryCleanupTask(row.getPointId(), row.getLearnerId(), row.getDeletionVersion(), row.getDeletedAt())).toList();
    }
    @Override public void completeCleanup(String pointId) { memories.deleteVectorCleanup(pointId); }
    @Override public boolean updateVectorSyncState(String memoryId, long expectedVersion, String state) {
        if (!List.of("PENDING", "SYNCED", "FAILED").contains(state)) throw invalid("Vector sync state is invalid.");
        return memories.updateVectorSyncState(memoryId, expectedVersion, state);
    }
    @Override public boolean updateVectorCleanupState(String pointId, String state) {
        if (!List.of("PENDING", "FAILED").contains(state)) throw invalid("Vector cleanup state is invalid.");
        return memories.updateVectorCleanupState(pointId, state);
    }

    private <T> T transact(java.util.function.Supplier<T> work) {
        MongoTransactionManager manager = transactionManagers.getIfAvailable();
        if (manager == null) throw new CompanionMemoryException(503, "COMPANION_STORAGE_UNAVAILABLE", "Memory storage is temporarily unavailable.", true);
        try { return new TransactionTemplate(manager).execute(status -> work.get()); }
        catch (CompanionMemoryException failure) { throw failure; }
        catch (org.springframework.dao.DataAccessException failure) {
            throw new CompanionMemoryException(503, "COMPANION_STORAGE_UNAVAILABLE", "Memory storage is temporarily unavailable.", true);
        }
    }

    private void afterCommit(CompanionMemory memory) {
        Runnable publish = () -> {
            try { events.publishEvent(new CompanionMemoryCommitted(memory.getId(), memory.getVersion())); }
            catch (RuntimeException ignored) { log.warn("Companion memory notification failed after commit"); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        else publish.run();
    }

    private void afterCommitDelete(String memoryId, String learnerId, long version) {
        Runnable publish = () -> {
            try { events.publishEvent(new CompanionMemoryForgotten(memoryId, learnerId, version)); }
            catch (RuntimeException ignored) { log.warn("Companion memory deletion notification failed after commit"); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        else publish.run();
    }

    private static void validateMutation(MemoryMutation mutation) {
        if (mutation == null || mutation.learnerId() == null || mutation.conversationId() == null || mutation.turnId() == null
                || mutation.fingerprint() == null || mutation.fingerprint().isBlank() || mutation.ordinal() < 0 || mutation.action() == null)
            throw invalid("Memory operation is invalid.");
        if (mutation.action() == MemoryMutation.Action.CREATED && (mutation.category() == null || mutation.source() == null))
            throw invalid("Memory category and source are required.");
    }
    private static String requireMemoryId(String value) {
        if (value == null || value.isBlank()) throw invalid("Memory ID is required.");
        return value;
    }
    private static long requireVersion(Long value) {
        if (value == null || value < 0) throw invalid("Memory version is invalid.");
        return value;
    }

    private CompanionMemory owned(String learnerId, String memoryId) {
        return memories.findCurrent(learnerId, memoryId).orElseThrow(CompanionMemoryServiceImpl::missing);
    }
    private CompanionMemoryException conflictOrMissing(String learnerId, String memoryId) {
        return memories.findCurrent(learnerId, memoryId).isPresent() ? conflict() : missing();
    }
    private static void validateContent(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 2000)
            throw invalid("Memory content must contain between 1 and 2000 characters.");
    }
    private static CompanionMemoryResponse response(CompanionMemory memory) {
        return new CompanionMemoryResponse(memory.getId(), memory.getCategory(), memory.getContent(), memory.getSource(),
                memory.getVersion(), memory.getCreatedAt(), memory.getUpdatedAt());
    }
    private static CompanionMemorySettingsUpdateResponse response(CompanionMemorySettings settings) {
        return new CompanionMemorySettingsUpdateResponse(Boolean.TRUE.equals(settings.getAutomaticMemoryEnabled()), settings.getUpdatedAt());
    }
    private static MemoryVectorDocument vectorDocument(CompanionMemory memory) {
        return new MemoryVectorDocument(memory.getId(), memory.getLearnerId(), memory.getCategory(), memory.getContent(),
                memory.getRetrievalMode(), memory.getVersion());
    }
    private static CompanionMemoryException missing() { return new CompanionMemoryException(404, "MEMORY_NOT_FOUND", "Memory was not found.", false); }
    private static CompanionMemoryException conflict() { return new CompanionMemoryException(409, "MEMORY_VERSION_CONFLICT", "Memory changed. Refresh and try again.", false); }
    private static CompanionMemoryException invalid(String detail) { return new CompanionMemoryException(HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST", detail, false); }
}
