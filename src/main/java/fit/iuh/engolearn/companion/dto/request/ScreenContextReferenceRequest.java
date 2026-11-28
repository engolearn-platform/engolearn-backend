package fit.iuh.engolearn.companion.dto.request;

import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScreenContextReferenceRequest(@NotNull ScreenContextType type,
        @Size(max = 128) String topicId, @Size(max = 128) String attemptId,
        @Size(max = 64) String stage, @Min(0) Integer itemIndex) {}
