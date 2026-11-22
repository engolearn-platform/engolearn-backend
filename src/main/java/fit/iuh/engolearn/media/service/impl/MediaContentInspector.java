package fit.iuh.engolearn.media.service.impl;

import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.media.utils.MediaPayloadTooLargeIOException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Locale;

/** Performs bounded signature inspection and counts exact bytes consumed by the provider. */
final class MediaContentInspector {

    private static final int SIGNATURE_BYTES = 12;

    private MediaContentInspector() {
    }

    static InspectedMedia inspect(InputStream source, String declaredContentType, long declaredSizeBytes, long maxSizeBytes) {
        if (source == null) throw new MediaUploadRejectedException("Media content is required");
        if (declaredSizeBytes <= 0) throw new MediaUploadRejectedException("Media size must be positive");
        if (declaredSizeBytes > maxSizeBytes) throw new MediaUploadRejectedException("Media exceeds the configured upload limit");
        if (declaredContentType == null || declaredContentType.isBlank()) {
            throw new MediaUploadRejectedException("Content type is required");
        }

        PushbackInputStream replayable = new PushbackInputStream(new NonClosingInputStream(source), SIGNATURE_BYTES);
        try {
            byte[] header = replayable.readNBytes(SIGNATURE_BYTES);
            replayable.unread(header);
            MediaFileDescriptor descriptor = detect(header);
            if (descriptor == null) throw new MediaUploadRejectedException("Unsupported or unrecognized media content");
            if (!matchesDeclaredType(declaredContentType, descriptor.contentType())) {
                throw new MediaUploadRejectedException("Declared content type does not match the uploaded file");
            }
            BoundedCountingInputStream counted = new BoundedCountingInputStream(replayable, maxSizeBytes);
            return new InspectedMedia(counted, descriptor, counted);
        } catch (IOException ex) {
            throw new MediaUploadRejectedException("Unable to inspect media content", ex);
        }
    }

    private static MediaFileDescriptor detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return new MediaFileDescriptor("image/jpeg", "jpg", MediaResourceType.IMAGE);
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new MediaFileDescriptor("image/png", "png", MediaResourceType.IMAGE);
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return new MediaFileDescriptor("image/webp", "webp", MediaResourceType.IMAGE);
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE")) {
            return new MediaFileDescriptor("audio/wav", "wav", MediaResourceType.VIDEO);
        }
        if (bytes.length >= 2 && (ascii(bytes, 0, "ID3")
                || ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0))) {
            return new MediaFileDescriptor("audio/mpeg", "mp3", MediaResourceType.VIDEO);
        }
        return null;
    }

    private static boolean matchesDeclaredType(String declared, String detected) {
        String normalized = declared.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if ("application/octet-stream".equals(normalized) || detected.equals(normalized)) return true;
        return "audio/wav".equals(detected)
                && ("audio/x-wav".equals(normalized) || "audio/wave".equals(normalized)
                || "audio/vnd.wave".equals(normalized));
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xFF) != expected[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    record InspectedMedia(InputStream content, MediaFileDescriptor descriptor, BoundedCountingInputStream counter) {
        long bytesRead() {
            return counter.bytesRead();
        }
    }

    static final class BoundedCountingInputStream extends FilterInputStream {
        private final long maxBytes;
        private long bytesRead;

        BoundedCountingInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) increment(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) increment(count);
            return count;
        }

        long bytesRead() {
            return bytesRead;
        }

        private void increment(long amount) throws MediaPayloadTooLargeIOException {
            if (bytesRead + amount > maxBytes) throw new MediaPayloadTooLargeIOException(maxBytes);
            bytesRead += amount;
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {
        private NonClosingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() {
            // The calling feature owns the original stream lifecycle.
        }
    }
}
