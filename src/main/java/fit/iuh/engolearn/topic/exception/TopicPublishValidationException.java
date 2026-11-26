package fit.iuh.engolearn.topic.exception;

import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;

import java.util.List;

public class TopicPublishValidationException extends RuntimeException {
    private final List<Violation> violations;

    public TopicPublishValidationException(List<Violation> violations) {
        super("Topic does not meet publish requirements.");
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
