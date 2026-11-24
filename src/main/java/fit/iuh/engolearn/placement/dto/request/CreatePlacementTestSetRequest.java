package fit.iuh.engolearn.placement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlacementTestSetRequest(@NotBlank @Size(max = 160) String name) {}
