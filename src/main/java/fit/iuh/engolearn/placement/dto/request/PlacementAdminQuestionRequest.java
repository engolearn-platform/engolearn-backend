package fit.iuh.engolearn.placement.dto.request;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlacementAdminQuestionRequest(
        @Size(max = 256) String code,
        PlacementSkill skill,
        PlacementLevel level,
        @Size(max = 10000) String prompt,
        @Size(max = 20000) String passage,
        @Size(max = 4) List<@Valid OptionRequest> options,
        @Size(max = 120) String correctOptionId,
        @Size(max = 20000) String explanation,
        @Size(max = 20000) String transcript,
        @Size(max = 256) String audioUrl,
        @PositiveOrZero Long revision
) {
    public record OptionRequest(@Size(max = 120) String id, @Size(max = 16) String label,
                                @Size(max = 2000) String text) {}
}
