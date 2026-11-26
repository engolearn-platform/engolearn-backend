package fit.iuh.engolearn.speaking.exception;

import org.springframework.http.HttpStatus;

public class SpeakingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final String activeSessionId;

    public SpeakingException(HttpStatus status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, null);
    }

    public SpeakingException(HttpStatus status, String code, String message, boolean retryable, String activeSessionId) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.activeSessionId = activeSessionId;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
    public String activeSessionId() { return activeSessionId; }
}
