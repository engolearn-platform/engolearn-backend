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

    /**
     * Uploads a WAV and verifies the delivered source bytes before registering its media ID.
     * The caller supplies the SHA-256 of the validated source and authorizes the upload.
     * Call outside a database transaction.
     */
    MediaUploadResultDto uploadVerifiedWav(UploadMediaCommandDto command, String sha256);

    MediaDeliveryDto resolve(String mediaId);

    /**
     * Verifies the complete delivered WAV against the registered size and caller's
     * SHA-256 of the validated source, within the caller's size limit.
     * Call after authorizing the owning content and outside database transactions.
     * Throws MediaNotFoundException or MediaStorageUnavailableException if unavailable.
     */
    void verifyWavDelivery(String mediaId, String sha256, long maximumBytes);
}
