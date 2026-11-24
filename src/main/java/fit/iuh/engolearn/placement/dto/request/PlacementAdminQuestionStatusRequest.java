package fit.iuh.engolearn.placement.dto.request;

import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlacementAdminQuestionStatusRequest(@NotNull PlacementQuestionStatus status,
                                                   @NotNull @PositiveOrZero Long revision) {}
