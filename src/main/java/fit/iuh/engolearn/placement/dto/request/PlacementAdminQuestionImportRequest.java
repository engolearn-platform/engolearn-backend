package fit.iuh.engolearn.placement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlacementAdminQuestionImportRequest(
        @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid PlacementAdminQuestionRequest> questions) {}
