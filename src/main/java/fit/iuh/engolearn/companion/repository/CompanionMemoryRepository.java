package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.CompanionMemorySettings;
import fit.iuh.engolearn.companion.model.MemoryVectorCleanup;
import fit.iuh.engolearn.companion.model.MemoryOperationReceipt;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanionMemoryRepository {
    record MemoryPage(List<CompanionMemory> items, String nextCursor) { }
    MemoryPage list(String learnerId, MemoryCategory category, Instant beforeUpdatedAt, String beforeId, int limit);
    Optional<CompanionMemory> findCurrent(String learnerId, String memoryId);
    Optional<CompanionMemory> findCurrentByIdAndVersion(String memoryId, long version);
    Optional<CompanionMemory> findDuplicate(String learnerId, MemoryCategory category, String normalizedContent);
    List<CompanionMemory> findCurrentByLearnerIdAndIds(String learnerId, Collection<String> memoryIds);
    List<CompanionMemory> listCurrentByLearnerId(String learnerId, MemoryRetrievalMode retrievalMode);
    List<CompanionMemory> listCurrentByLearnerId(String learnerId, MemoryRetrievalMode retrievalMode, int limit);
    List<CompanionMemory> listCurrentForSync(int limit);
    List<CompanionMemory> listCurrentForReconciliation(String afterId, int limit);
    CompanionMemory insert(CompanionMemory memory);
    Optional<CompanionMemory> update(String learnerId, String memoryId, long expectedVersion, String content, Instant now);
    Optional<CompanionMemory> incrementVersion(String learnerId, String memoryId, long expectedVersion, Instant now);
    boolean deleteCurrent(String learnerId, String memoryId, long expectedVersion);
    Optional<CompanionMemorySettings> findSettings(String learnerId);
    CompanionMemorySettings ensureSettings(String learnerId);
    CompanionMemorySettings setAutomaticMemoryEnabled(String learnerId, boolean enabled, Instant now);
    boolean fenceAutomaticWrite(String learnerId, long expectedForgetRevision, Instant now);
    boolean fenceExplicitWrite(String learnerId, Instant now);
    long advanceForgetRevision(String learnerId, Instant now);
    void insertCleanup(MemoryVectorCleanup cleanup);
    List<MemoryVectorCleanup> listVectorCleanups(int limit);
    List<MemoryVectorCleanup> listPendingVectorCleanups(int limit);
    List<MemoryVectorCleanup> listVectorCleanupsForReconciliation(String afterPointId, int limit);
    void deleteVectorCleanup(String pointId);
    boolean updateVectorCleanupState(String pointId, String state);
    boolean updateVectorSyncState(String memoryId, long expectedVersion, String state);
    Optional<MemoryOperationReceipt> findReceipt(String conversationId, String turnId, int ordinal);
    void insertReceipt(MemoryOperationReceipt receipt);
}
