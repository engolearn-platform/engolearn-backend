package fit.iuh.engolearn.media.contract.dto;

import java.net.URI;
import java.time.Instant;

/** Generated delivery location; expiresAt is null for public URLs. */
public record MediaDeliveryDto(
        URI url,
        Instant expiresAt,
        String contentType) {
}
