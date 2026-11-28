package fit.iuh.engolearn.companion.dto.response;

import java.time.Instant;

public record CompanionMemorySettingsUpdateResponse(boolean automaticMemoryEnabled, Instant updatedAt) { }
