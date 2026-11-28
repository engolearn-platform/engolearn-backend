package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.model.enums.MemorySource;

public record MemoryMutation(String learnerId, String conversationId, String turnId, int ordinal, String fingerprint,
                             Action action, String memoryId, MemoryCategory category, String content, MemorySource source,
                             MemoryRetrievalMode retrievalMode, Long expectedVersion, boolean automatic,
                             boolean admissionAutomaticEnabled, long expectedForgetRevision) {
    public enum Action { CREATED, UPDATED, DELETED }
}
