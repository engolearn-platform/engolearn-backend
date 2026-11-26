package fit.iuh.engolearn.companion.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateCompanionMemorySettingsRequest(@NotNull Boolean automaticMemoryEnabled) { }
