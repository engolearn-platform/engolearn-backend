package fit.iuh.engolearn.microskill.dto.request;

import fit.iuh.engolearn.microskill.model.enums.*;
import jakarta.validation.constraints.*;
public record CreateMicroSkillRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}") String code,
        @NotBlank @Size(max = 200) String canonicalName,
        @NotBlank @Size(max = 200) String displayNameVi,
        @NotBlank @Size(max = 4000) String descriptionVi,
        @NotNull MicroSkillCategory skill, @NotNull MicroSkillStatus status,
        @Min(0) int displayOrder) {}
