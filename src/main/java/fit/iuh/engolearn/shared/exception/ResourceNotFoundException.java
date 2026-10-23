package fit.iuh.engolearn.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Envelope code {@code RESOURCE_NOT_FOUND} → HTTP 404, no {@code fieldErrors}.
 * The caller (service) supplies the developer-facing message, e.g.
 * {@code "Topic not found: <id>"} (design record F05 §4.3).
 */
public class ResourceNotFoundException extends ApiException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(CODE, HttpStatus.NOT_FOUND, message);
    }
}
