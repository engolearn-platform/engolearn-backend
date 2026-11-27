package fit.iuh.engolearn.assessment.dto.response;

import java.net.URI;
import java.time.Instant;

public record AssessmentMediaResponse(String mimeType, String base64, URI url, Instant expiresAt) {
    public AssessmentMediaResponse(String mimeType, String base64) {
        this(mimeType, base64, null, null);
    }
}
