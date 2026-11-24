package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record TopicPublishReadinessResponse(boolean ready, List<Violation> violations) {
    public TopicPublishReadinessResponse {
        violations = List.copyOf(violations);
    }

    public record Violation(String path, String code, String message) {
    }
}
