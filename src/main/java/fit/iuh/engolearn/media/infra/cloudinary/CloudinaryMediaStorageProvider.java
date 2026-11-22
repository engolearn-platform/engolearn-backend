package fit.iuh.engolearn.media.infra.cloudinary;

import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.media.utils.MediaPayloadTooLargeIOException;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import fit.iuh.engolearn.shared.media.CloudinaryMediaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class CloudinaryMediaStorageProvider implements MediaStorageProvider {

    private final CloudinaryMediaClient client;

    public CloudinaryMediaStorageProvider(CloudinaryMediaClient client) {
        this.client = client;
    }

    @Override
    public MediaStorageMetadataVo upload(
            String publicId,
            MediaFileDescriptor descriptor,
            MediaAccessPolicy accessPolicy,
            InputStream content) {
        requireConfigured();
        MediaDeliveryType deliveryType = deliveryType(accessPolicy);
        try {
            Map<?, ?> result = client.cloudinary().uploader().upload(content, Map.of(
                    "resource_type", descriptor.resourceType().cloudinaryValue(),
                    "type", deliveryType.cloudinaryValue(),
                    "public_id", publicId,
                    "overwrite", false,
                    "format", descriptor.format(),
                    "secure", true));
            Object versionValue = result.get("version");
            Long version = versionValue instanceof Number number ? number.longValue() : null;
            Object returnedPublicId = result.get("public_id");
            Object returnedFormat = result.get("format");
            return MediaStorageMetadataVo.builder()
                    .provider(MediaProvider.CLOUDINARY)
                    .assetId(stringValue(result.get("asset_id")))
                    .publicId(returnedPublicId == null ? publicId : String.valueOf(returnedPublicId))
                    .resourceType(descriptor.resourceType())
                    .deliveryType(deliveryType)
                    .format(returnedFormat == null ? descriptor.format() : String.valueOf(returnedFormat))
                    .version(version)
                    .build();
        } catch (Exception ex) {
            cleanupPartialUpload(publicId, descriptor.resourceType(), deliveryType);
            if (hasCause(ex, MediaPayloadTooLargeIOException.class)) {
                throw new MediaUploadRejectedException("Media exceeds the configured upload limit", ex);
            }
            throw new MediaStorageUnavailableException("Cloudinary media upload failed", ex);
        }
    }

    @Override
    public URI publicUrl(MediaStorageMetadataVo storage) {
        requireConfigured();
        try {
            String url = client.cloudinary().url()
                    .secure(true)
                    .resourceType(storage.getResourceType().cloudinaryValue())
                    .type(storage.getDeliveryType().cloudinaryValue())
                    .format(storage.getFormat())
                    .generate(storage.getPublicId());
            return URI.create(url);
        } catch (Exception ex) {
            throw new MediaStorageUnavailableException("Cloudinary public URL generation failed", ex);
        }
    }

    @Override
    public URI privateDownloadUrl(MediaStorageMetadataVo storage, Instant expiresAt) {
        requireConfigured();
        try {
            String url = client.cloudinary().privateDownload(storage.getPublicId(), storage.getFormat(), Map.of(
                    "resource_type", storage.getResourceType().cloudinaryValue(),
                    "type", storage.getDeliveryType().cloudinaryValue(),
                    "expires_at", expiresAt.getEpochSecond()));
            return URI.create(url);
        } catch (Exception ex) {
            throw new MediaStorageUnavailableException("Cloudinary protected URL generation failed", ex);
        }
    }

    @Override
    public void destroy(MediaStorageMetadataVo storage) {
        requireConfigured();
        try {
            client.cloudinary().uploader().destroy(storage.getPublicId(), Map.of(
                    "resource_type", storage.getResourceType().cloudinaryValue(),
                    "type", storage.getDeliveryType().cloudinaryValue(),
                    "invalidate", true));
        } catch (Exception ex) {
            throw new MediaStorageUnavailableException("Cloudinary compensation cleanup failed", ex);
        }
    }

    private void cleanupPartialUpload(String publicId, fit.iuh.engolearn.media.model.enums.MediaResourceType resourceType,
                                      MediaDeliveryType deliveryType) {
        try {
            client.cloudinary().uploader().destroy(publicId, Map.of(
                    "resource_type", resourceType.cloudinaryValue(),
                    "type", deliveryType.cloudinaryValue(),
                    "invalidate", true));
        } catch (Exception cleanupFailure) {
            log.warn("Cloudinary partial-upload cleanup failed for publicId={}", publicId, cleanupFailure);
        }
    }

    private MediaDeliveryType deliveryType(MediaAccessPolicy accessPolicy) {
        return accessPolicy == MediaAccessPolicy.PUBLIC ? MediaDeliveryType.UPLOAD : MediaDeliveryType.AUTHENTICATED;
    }

    private void requireConfigured() {
        if (!client.configured()) {
            throw new MediaStorageUnavailableException("Cloudinary media storage is not configured");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
