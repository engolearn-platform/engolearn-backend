package fit.iuh.engolearn.assessment.dto.request;

import fit.iuh.engolearn.assessment.model.enums.AssessmentSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MicroSkillRequest {
    private MicroSkillRequest() {}

    public record Create(@NotBlank @Pattern(regexp = "[RL][0-9]{2}") String code,
                         @NotBlank @Size(max = 180) String label, @NotNull AssessmentSection section) {}

    public record Rename(@NotBlank @Size(max = 180) String label) {}
}
