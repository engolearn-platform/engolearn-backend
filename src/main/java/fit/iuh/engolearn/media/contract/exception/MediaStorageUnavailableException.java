package fit.iuh.engolearn.media.contract.exception;

/** The media provider or persistence operation could not complete. */
public class MediaStorageUnavailableException extends RuntimeException {
    public MediaStorageUnavailableException(String message) {
        super(message);
    }

    public MediaStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
