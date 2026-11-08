package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssessmentAudioFailureRequest(@Min(0) @Max(25) int unitIndex,
        @NotBlank @Pattern(regexp="LOAD_FAILED|PLAYBACK_FAILED|AUTOPLAY_BLOCKED") String reason) {}
