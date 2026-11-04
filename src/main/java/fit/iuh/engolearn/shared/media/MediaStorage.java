package fit.iuh.engolearn.shared.media;

import java.net.URI;
import java.time.Duration;

public interface MediaStorage {
    record StoredAudio(String publicId, String format) {}

    StoredAudio uploadPrivateAudio(String publicId, String format, byte[] bytes);
    URI privateAudioUrl(String publicId, String format, Duration lifetime);
}
