package fit.iuh.engolearn.placement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePlacementTestSetRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> questionIds,
        @NotNull @PositiveOrZero Long revision) {}
