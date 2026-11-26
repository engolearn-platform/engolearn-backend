package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;

public record MemoryVectorDocument(String id, String learnerId, MemoryCategory category, String content,
                                   MemoryRetrievalMode retrievalMode, long version) { }
