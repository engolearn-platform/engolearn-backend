package fit.iuh.engolearn.shared.web;

import fit.iuh.engolearn.shared.exception.ApiException;
import fit.iuh.engolearn.shared.exception.ValidationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Renders the frozen F05 error envelope (design record §4.0.1) for the
 * whole application surface:
 *
 * <ul>
 *   <li>{@link ApiException} → its own {@code code} + HTTP status;</li>
 *   <li>Bean Validation on request bodies → 400 {@code VALIDATION_ERROR} with field errors;</li>
 *   <li>malformed JSON / unparseable body → 400 {@code VALIDATION_ERROR}, message-level only;</li>
 *   <li>query-param binding failures → 400 {@code VALIDATION_ERROR} with
 *       {@code fieldErrors[].field} = param name.</li>
 * </ul>
 *
 * <p>Statuses not handled here (no-route 404, 405, 500) fall through to
 * Spring's default error body — out of envelope scope for F05.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        List<ApiFieldError> fieldErrors = null;
        if (exception instanceof ValidationFailedException validation && !validation.getFieldErrors().isEmpty()) {
            fieldErrors = validation.getFieldErrors();
        }
        ApiError body = new ApiError(exception.getCode(), exception.getMessage(), fieldErrors);
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return badRequest(new ApiError(ValidationFailedException.CODE, ValidationFailedException.DEFAULT_MESSAGE, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return badRequest(ApiError.of(ValidationFailedException.CODE, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String field = exception.getName();
        String detail = "Invalid value '" + exception.getValue() + "' for parameter '" + field + "'";
        return badRequest(new ApiError(ValidationFailedException.CODE, ValidationFailedException.DEFAULT_MESSAGE,
                List.of(new ApiFieldError(field, detail))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        String field = exception.getParameterName();
        String detail = "Missing required request parameter '" + field + "'";
        return badRequest(new ApiError(ValidationFailedException.CODE, ValidationFailedException.DEFAULT_MESSAGE,
                List.of(new ApiFieldError(field, detail))));
    }

    private static ResponseEntity<ApiError> badRequest(ApiError body) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
