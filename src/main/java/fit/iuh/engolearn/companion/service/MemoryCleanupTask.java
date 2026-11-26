package fit.iuh.engolearn.companion.service;

import java.time.Instant;

public record MemoryCleanupTask(String pointId, String learnerId, long deletionVersion, Instant deletedAt) { }
