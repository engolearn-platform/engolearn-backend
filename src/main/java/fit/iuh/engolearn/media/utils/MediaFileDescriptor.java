package fit.iuh.engolearn.media.utils;

import fit.iuh.engolearn.media.model.enums.MediaResourceType;

/** Detected technical file properties, never trusted client metadata. */
public record MediaFileDescriptor(
        String contentType,
        String format,
        MediaResourceType resourceType) {
}
