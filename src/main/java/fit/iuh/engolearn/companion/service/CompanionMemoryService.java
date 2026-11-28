package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.dto.response.CompanionMemoryPageResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemoryResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsResponse;
import fit.iuh.engolearn.companion.dto.response.CompanionMemorySettingsUpdateResponse;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import java.util.Collection;
import java.util.List;

public interface CompanionMemoryService {
    CompanionMemoryPageResponse list(String learnerId, MemoryCategory category, String before, int limit);
    CompanionMemoryResponse update(String learnerId, String memoryId, String content, long expectedVersion);
    void forget(String learnerId, String memoryId, long expectedVersion);
    CompanionMemorySettingsResponse settings(String learnerId);
    CompanionMemorySettingsUpdateResponse updateSettings(String learnerId, boolean enabled);
    MemorySettingsSnapshot settingsSnapshot(String learnerId);
    MemoryOperationResult mutate(MemoryMutation mutation, Runnable conversationFence);
    List<MemoryVectorDocument> findCurrentByIds(String learnerId, Collection<String> ids);
    java.util.Optional<MemoryVectorDocument> findCurrentByIdAndVersion(String memoryId, long version);
    List<MemoryVectorDocument> listCurrent(String learnerId, fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode mode);
    List<MemoryVectorDocument> listCurrent(String learnerId, fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode mode, int limit);
    List<MemoryVectorDocument> listCurrentForSync(int limit);
    List<MemoryCleanupTask> listCleanupTasks(int limit);
    List<MemoryCleanupTask> listPendingCleanupTasks(int limit);
    List<MemoryCleanupTask> listCleanupTasksForReconciliation(String afterPointId, int limit);
    void completeCleanup(String pointId);
    boolean updateVectorSyncState(String memoryId, long expectedVersion, String state);
    boolean updateVectorCleanupState(String pointId, String state);
}
