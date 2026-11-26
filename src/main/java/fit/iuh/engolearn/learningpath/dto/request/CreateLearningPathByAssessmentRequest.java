package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CreateLearningPathByAssessmentRequest(
        @NotBlank @Size(max = 100) String assessmentAttemptId,
        @NotEmpty @Size(max = 5) List<@NotBlank String> microSkills,
        @NotBlank @Pattern(regexp = "A1|A2|B1|B2|C1|C2") String level
) {
}
