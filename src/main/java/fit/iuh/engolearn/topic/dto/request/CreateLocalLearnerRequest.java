package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLocalLearnerRequest(@NotBlank @Size(max = 100) String displayName, @NotNull CefrLevel cefrLevel) {}
