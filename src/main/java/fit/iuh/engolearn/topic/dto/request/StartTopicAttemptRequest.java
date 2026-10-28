package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartTopicAttemptRequest(@NotBlank @Pattern(regexp = "LEARN|RETRY") String mode,
                                       String retrySessionId, String replaceRetrySessionId) {}
