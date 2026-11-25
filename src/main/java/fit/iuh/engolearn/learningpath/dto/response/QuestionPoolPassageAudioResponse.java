package fit.iuh.engolearn.learningpath.dto.response;

import java.time.Instant;

public record QuestionPoolPassageAudioResponse(
        String url, Instant expiresAt, String contentType, Integer durationSec, String format) {
}
