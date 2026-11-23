package fit.iuh.engolearn.media.utils;

import java.io.IOException;

/** Internal stream failure used to stop an upload as soon as the global limit is exceeded. */
public class MediaPayloadTooLargeIOException extends IOException {
    public MediaPayloadTooLargeIOException(long maxBytes) {
        super("Media payload exceeds the configured limit of " + maxBytes + " bytes");
    }
}
