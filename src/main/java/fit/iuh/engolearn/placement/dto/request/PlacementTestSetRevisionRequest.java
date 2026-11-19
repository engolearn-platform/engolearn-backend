package fit.iuh.engolearn.placement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlacementTestSetRevisionRequest(@NotNull @PositiveOrZero Long revision) {}
