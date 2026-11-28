package fit.iuh.engolearn.topic.contract.exception;

/** Invalid caller input for the read-only Topic Companion contract. */
public class TopicCompanionInvalidQueryException extends RuntimeException {
    public TopicCompanionInvalidQueryException(String message) {
        super(message);
    }
}
