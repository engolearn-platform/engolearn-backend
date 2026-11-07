package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateLearningPathRequest(
        @NotBlank @Size(max = 100) String attemptId,
        @NotEmpty @Size(max = 5) List<@NotBlank String> microSkills
) {
}
