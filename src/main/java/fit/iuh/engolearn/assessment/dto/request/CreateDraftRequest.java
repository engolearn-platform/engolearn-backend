package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.*;
public record CreateDraftRequest(@NotBlank @Pattern(regexp="[a-zA-Z0-9][a-zA-Z0-9_.-]{0,79}") String series,
                                 @NotNull @Size(max=180) String name) {}
