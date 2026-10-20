package fit.iuh.engolearn.topic.exception;

/**
 * Raised when a topic cannot be shown: it does not exist, it has never been published, or
 * its release pointer no longer resolves. All three cases are reported the same way so the
 * response never reveals content of an unpublished topic.
 */
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(String message) {
        super(message);
    }
}