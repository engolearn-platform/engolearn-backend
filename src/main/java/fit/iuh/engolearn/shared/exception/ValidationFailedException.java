package fit.iuh.engolearn.shared.exception;

import fit.iuh.engolearn.shared.web.ApiFieldError;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Envelope code {@code VALIDATION_ERROR} → HTTP 400 (design record F05 §4.0.1).
 * Carries field-level errors when the failure is attributable to request
 * fields or query params; message-level only (empty field errors) for
 * malformed bodies.
 */
public class ValidationFailedException extends ApiException {

    public static final String CODE = "VALIDATION_ERROR";
    public static final String DEFAULT_MESSAGE = "Request validation failed";

    private final List<ApiFieldError> fieldErrors;

    public ValidationFailedException(List<ApiFieldError> fieldErrors) {
        this(DEFAULT_MESSAGE, fieldErrors);
    }

    public ValidationFailedException(String message, List<ApiFieldError> fieldErrors) {
        super(CODE, HttpStatus.BAD_REQUEST, message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** Single field-level failure with the canonical message. */
    public static ValidationFailedException forField(String field, String message) {
        return new ValidationFailedException(List.of(new ApiFieldError(field, message)));
    }

    /** Never {@code null}; empty means "message-level only" (fieldErrors omitted). */
    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}
