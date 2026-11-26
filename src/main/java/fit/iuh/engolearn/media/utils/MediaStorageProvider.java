package fit.iuh.engolearn.media.utils;

import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

/** Internal Media-owned port implemented by the selected storage provider adapter. */
public interface MediaStorageProvider {

    MediaStorageMetadataVo upload(
            String publicId,
            MediaFileDescriptor descriptor,
            MediaAccessPolicy accessPolicy,
            InputStream content);

    URI publicUrl(MediaStorageMetadataVo storage);

    URI privateDownloadUrl(MediaStorageMetadataVo storage, Instant expiresAt);

    /** Internal compensation only; not exposed through MediaManagement. */
    void destroy(MediaStorageMetadataVo storage);
}
