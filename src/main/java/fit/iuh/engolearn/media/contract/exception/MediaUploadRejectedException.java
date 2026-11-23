package fit.iuh.engolearn.media.contract.exception;

/** Upload input violates a generic technical media constraint. */
public class MediaUploadRejectedException extends RuntimeException {
    public MediaUploadRejectedException(String message) {
        super(message);
    }

    public MediaUploadRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
