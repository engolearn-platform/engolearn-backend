package fit.iuh.engolearn.topic.exception;

import java.util.List;

public class TopicMutationValidationException extends RuntimeException {
    private final List<FieldError> fieldErrors;

    public TopicMutationValidationException(String field, String message) {
        super("Request validation failed");
        this.fieldErrors = List.of(new FieldError(field, message));
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String message) {}
}
