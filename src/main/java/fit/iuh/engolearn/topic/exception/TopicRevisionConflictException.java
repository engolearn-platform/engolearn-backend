package fit.iuh.engolearn.topic.exception;

public class TopicRevisionConflictException extends RuntimeException {
    private final String code;

    public TopicRevisionConflictException() {
        this("Topic revision is stale. Reload the topic before saving.", "CONFLICT");
    }

    public TopicRevisionConflictException(String message) {
        this(message, "CONFLICT");
    }

    public TopicRevisionConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
