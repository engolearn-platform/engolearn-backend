package fit.iuh.engolearn.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The frozen F05 error envelope (design record §4.0.1):
 * {@code {code, message, fieldErrors?: [{field, message}]}}.
 *
 * <p>{@code fieldErrors} is omitted entirely when empty or not applicable —
 * {@link JsonInclude.Include#NON_EMPTY} guarantees the key is absent for both
 * {@code null} and empty lists.</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<ApiFieldError> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    /** Envelope without field-level detail (e.g. 404, malformed body). */
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
