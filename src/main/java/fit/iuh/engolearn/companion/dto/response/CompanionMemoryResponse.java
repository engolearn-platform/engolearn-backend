package fit.iuh.engolearn.companion.dto.response;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemorySource;
import java.time.Instant;

public record CompanionMemoryResponse(String id, MemoryCategory category, String content, MemorySource source,
                                      long version, Instant createdAt, Instant updatedAt) { }
