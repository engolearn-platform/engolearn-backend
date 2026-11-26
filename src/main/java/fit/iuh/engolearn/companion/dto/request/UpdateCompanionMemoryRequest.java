package fit.iuh.engolearn.companion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCompanionMemoryRequest(@NotBlank @Size(max = 4000) String content,
                                           @NotNull @PositiveOrZero Long expectedVersion) { }
