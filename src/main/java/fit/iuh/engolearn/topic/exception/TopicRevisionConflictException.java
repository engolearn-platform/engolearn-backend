package fit.iuh.engolearn.topic.exception;

public class TopicRevisionConflictException extends RuntimeException {
    public TopicRevisionConflictException() {
        super("Topic revision is stale. Reload the topic before saving.");
    }
}
