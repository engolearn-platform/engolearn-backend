package fit.iuh.engolearn.media.contract.dto;

import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;

/** Safe upload result for the owning feature to persist as a media reference. */
public record MediaUploadResultDto(
        String mediaId,
        String contentType,
        long sizeBytes,
        MediaAccessPolicy accessPolicy) {
}
