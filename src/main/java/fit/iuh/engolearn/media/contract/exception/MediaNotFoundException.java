package fit.iuh.engolearn.media.contract.exception;

/** The requested internal media identifier does not exist. */
public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String mediaId) {
        super("Media not found: " + mediaId);
    }
}
