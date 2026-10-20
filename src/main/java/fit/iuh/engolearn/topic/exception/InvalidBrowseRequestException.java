package fit.iuh.engolearn.topic.exception;

/**
 * Raised when a browse request cannot be served as sent: an out-of-range page size, an
 * unknown level or sort order, or a cursor that cannot be decoded or does not belong to the
 * current filters and sort order.
 */
public class InvalidBrowseRequestException extends RuntimeException {

    public InvalidBrowseRequestException(String message) {
        super(message);
    }
}