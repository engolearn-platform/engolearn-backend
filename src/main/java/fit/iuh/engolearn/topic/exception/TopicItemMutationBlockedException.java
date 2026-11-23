package fit.iuh.engolearn.topic.exception;

public class TopicItemMutationBlockedException extends RuntimeException {
    private final String code;

    public TopicItemMutationBlockedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
