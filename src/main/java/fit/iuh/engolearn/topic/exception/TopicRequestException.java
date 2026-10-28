package fit.iuh.engolearn.topic.exception;

public class TopicRequestException extends RuntimeException {
    private final int status;
    private final String code;
    public TopicRequestException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}
