package fit.iuh.engolearn.media.contract.dto;

import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;

import java.io.InputStream;

/**
 * Upload input supplied by the owning feature.
 *
 * @param purpose opaque feature-owned metadata; Media must not branch on its value
 * @param accessPolicy access policy selected by the owning feature
 * @param originalFilename optional diagnostic filename
 * @param contentType client-declared MIME type, verified against file content
 * @param sizeBytes declared byte count, verified against actual stream consumption
 * @param content one-shot stream consumed synchronously; caller owns opening and closing it
 */
public record UploadMediaCommandDto(
        String purpose,
        MediaAccessPolicy accessPolicy,
        String originalFilename,
        String contentType,
        long sizeBytes,
        InputStream content) {
}
