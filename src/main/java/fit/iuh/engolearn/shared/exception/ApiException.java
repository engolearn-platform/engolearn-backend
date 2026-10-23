package fit.iuh.engolearn.shared.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Base type for errors that map to the frozen API error envelope
 * {@code {code, message, fieldErrors?}} (design record F05 §4.0.1).
 *
 * <p>Carries the envelope {@code code} and the HTTP status. Feature-specific
 * exceptions extend this class; the global handler in {@code shared.web}
 * renders any {@link ApiException} to the envelope.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatusCode statusCode;

    protected ApiException(String code, HttpStatusCode statusCode, String message) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
