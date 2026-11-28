package fit.iuh.engolearn.microskill.dto.request;

import fit.iuh.engolearn.microskill.model.enums.MicroSkillStatus;
import jakarta.validation.constraints.*;
public record UpdateMicroSkillRequest(
        @NotNull @Min(1) @Max(2147483646) Integer revision,
        @NotBlank @Size(max = 200) String displayNameVi,
        @NotBlank @Size(max = 4000) String descriptionVi,
        @NotNull MicroSkillStatus status, @Min(0) int displayOrder) {}
