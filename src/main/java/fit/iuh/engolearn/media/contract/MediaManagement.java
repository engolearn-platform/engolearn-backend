package fit.iuh.engolearn.media.contract;

import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;

/**
 * Public in-process capability for storing and resolving media.
 *
 * <p>Callers own feature-specific authorization and upload policy. A caller must
 * authorize access to the owning content before resolving a PROTECTED asset.
 * This contract does not evaluate Topic, Quiz, subscription, or other entitlements.</p>
 *
 * <p>The upload stream is consumed synchronously before this method returns.
 * The caller owns opening and closing the supplied stream.</p>
 */
public interface MediaManagement {

    MediaUploadResultDto upload(UploadMediaCommandDto command);

    MediaDeliveryDto resolve(String mediaId);

    /**
     * Reads the WAV signature through a fresh provider delivery URL.
     * Call after authorizing the owning content and outside database transactions.
     * Throws MediaNotFoundException or MediaStorageUnavailableException if unavailable.
     */
    void verifyWavDelivery(String mediaId);
}
