package fit.iuh.engolearn.shared.web;

/**
 * One entry of the frozen envelope's {@code fieldErrors} array:
 * {@code {field, message}} (design record F05 §4.0.1).
 */
public record ApiFieldError(String field, String message) {
}
