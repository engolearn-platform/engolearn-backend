package fit.iuh.engolearn.shared.media;

import com.cloudinary.Cloudinary;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class CloudinaryMediaStorage implements MediaStorage {
    private final Cloudinary cloudinary;
    private final boolean configured;

    CloudinaryMediaStorage(Cloudinary cloudinary, boolean configured) {
        this.cloudinary = cloudinary;
        this.configured = configured;
    }

    @Override
    public StoredAudio uploadPrivateAudio(String publicId, String format, byte[] bytes) {
        requireConfigured();
        if (!"wav".equals(format) && !"mp3".equals(format)) {
            throw new IllegalArgumentException("Unsupported audio format");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, Map.of(
                    "resource_type", "video", "type", "authenticated", "public_id", publicId,
                    "overwrite", false, "format", format));
            return new StoredAudio(String.valueOf(result.get("public_id")),
                    String.valueOf(result.get("format")));
        } catch (Exception ex) {
            throw new IllegalStateException("Private audio upload failed", ex);
        }
    }

    @Override
    public URI privateAudioUrl(String publicId, String format, Duration lifetime) {
        requireConfigured();
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Audio URL lifetime must be at most one minute");
        }
        try {
            String url = cloudinary.privateDownload(publicId, format, Map.of(
                    "resource_type", "video", "type", "authenticated",
                    "expires_at", Instant.now().plus(lifetime).getEpochSecond()));
            return URI.create(url);
        } catch (Exception ex) {
            throw new IllegalStateException("Private audio URL creation failed", ex);
        }
    }

    private void requireConfigured() {
        if (!configured) throw new IllegalStateException("Cloudinary media storage is not configured");
    }
}
