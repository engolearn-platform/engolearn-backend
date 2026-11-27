package fit.iuh.engolearn.media.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.model.MediaAsset;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.repository.MediaAssetRepository;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class MediaManagementImpl implements MediaManagement {

    private static final int DELIVERY_TIMEOUT_MS = 3000;

    private final MediaAssetRepository repository;
    private final MediaStorageProvider storage;
    private final CurrentUserProvider currentUserProvider;
    private final long maxUploadSizeBytes;
    private final long maxImageUploadSizeBytes;
    private final Duration protectedUrlLifetime;

    public MediaManagementImpl(
            MediaAssetRepository repository,
            MediaStorageProvider storage,
            CurrentUserProvider currentUserProvider,
            @Value("${engolearn.media.max-upload-size-bytes:26214400}") long maxUploadSizeBytes,
            @Value("${engolearn.media.max-image-upload-size-bytes:10000000}") long maxImageUploadSizeBytes,
            @Value("${engolearn.media.protected-url-lifetime:60s}") Duration protectedUrlLifetime) {
        if (maxUploadSizeBytes <= 0 || maxImageUploadSizeBytes <= 0
                || maxImageUploadSizeBytes > maxUploadSizeBytes) {
            throw new IllegalArgumentException("Media upload limits must be positive and image limit cannot exceed global limit");
        }
        if (protectedUrlLifetime == null || protectedUrlLifetime.compareTo(Duration.ofSeconds(1)) < 0
                || protectedUrlLifetime.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Protected media URL lifetime must be between 1 and 60 seconds");
        }
        this.repository = repository;
        this.storage = storage;
        this.currentUserProvider = currentUserProvider;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
        this.maxImageUploadSizeBytes = maxImageUploadSizeBytes;
        this.protectedUrlLifetime = protectedUrlLifetime;
    }

    @Override
    public MediaUploadResultDto upload(UploadMediaCommandDto command) {
        validateCommand(command);
        MediaContentInspector.InspectedMedia inspected = MediaContentInspector.inspect(
                command.content(), command.contentType(), command.sizeBytes(),
                maxUploadSizeBytes, maxImageUploadSizeBytes);
        String createdBy = currentUserProvider.getCurrentUser().map(user -> user.id()).orElse(null);

        String mediaId = UUID.randomUUID().toString();
        String publicId = "engolearn/media/" + mediaId;
        MediaStorageMetadataVo storageMetadata = storage.upload(
                publicId, inspected.descriptor(), command.accessPolicy(), inspected.content());

        long actualSize = inspected.bytesRead();
        if (actualSize != command.sizeBytes()) {
            compensate(storageMetadata, mediaId);
            throw new MediaUploadRejectedException("Declared media size does not match the uploaded content");
        }

        MediaAsset asset = MediaAsset.builder()
                .id(mediaId)
                .purpose(command.purpose().trim())
                .accessPolicy(command.accessPolicy())
                .originalFilename(safeFilename(command.originalFilename()))
                .contentType(inspected.descriptor().contentType())
                .sizeBytes(actualSize)
                .storage(storageMetadata)
                .createdBy(createdBy)
                .build();

        try {
            repository.save(asset);
        } catch (RuntimeException persistenceFailure) {
            log.error("Media metadata persistence failed after Cloudinary upload: mediaId={}",
                    mediaId, persistenceFailure);
            compensate(storageMetadata, mediaId);
            throw new MediaStorageUnavailableException("Media metadata could not be persisted", persistenceFailure);
        }

        return new MediaUploadResultDto(mediaId, asset.getContentType(), actualSize, asset.getAccessPolicy());
    }

    @Override
    public MediaDeliveryDto resolve(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) throw new MediaNotFoundException(String.valueOf(mediaId));
        MediaAsset asset = repository.findById(mediaId).orElseThrow(() -> new MediaNotFoundException(mediaId));

        if (asset.getAccessPolicy() == MediaAccessPolicy.PUBLIC) {
            return new MediaDeliveryDto(storage.publicUrl(asset.getStorage()), null, asset.getContentType());
        }
        if (asset.getAccessPolicy() != MediaAccessPolicy.PROTECTED) {
            throw new MediaStorageUnavailableException("Media has an unsupported access policy");
        }

        Instant expiresAt = Instant.ofEpochSecond(Instant.now().plus(protectedUrlLifetime).getEpochSecond());
        return new MediaDeliveryDto(storage.privateDownloadUrl(asset.getStorage(), expiresAt),
                expiresAt, asset.getContentType());
    }

    @Override
    public void verifyWavDelivery(String mediaId) {
        var delivery = resolve(mediaId);
        if (!"audio/wav".equals(delivery.contentType())) {
            throw new MediaStorageUnavailableException("Media is not a WAV");
        }

        // Only connect to a URL generated by the storage adapter for this persisted asset.
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) delivery.url().toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(DELIVERY_TIMEOUT_MS);
            connection.setReadTimeout(DELIVERY_TIMEOUT_MS);
            connection.setRequestProperty("Range", "bytes=0-" + (MediaContentInspector.SIGNATURE_BYTES - 1));
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                throw new MediaStorageUnavailableException("WAV delivery returned HTTP " + status);
            }
            try (var input = connection.getInputStream()) {
                if (!MediaContentInspector.isWav(input.readNBytes(MediaContentInspector.SIGNATURE_BYTES))) {
                    throw new MediaStorageUnavailableException("WAV delivery did not return a WAV signature");
                }
            }
        } catch (IOException | ClassCastException ex) {
            // An I/O exception may contain the signed URL.
            throw new MediaStorageUnavailableException("WAV delivery could not be read");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void validateCommand(UploadMediaCommandDto command) {
        if (command == null) throw new MediaUploadRejectedException("Upload command is required");
        if (command.purpose() == null || command.purpose().isBlank()) {
            throw new MediaUploadRejectedException("Media purpose is required");
        }
        if (command.accessPolicy() == null) throw new MediaUploadRejectedException("Media access policy is required");
        if (command.content() == null) throw new MediaUploadRejectedException("Media content is required");
        if (command.sizeBytes() <= 0 || command.sizeBytes() > maxUploadSizeBytes) {
            throw new MediaUploadRejectedException("Media size is outside the allowed range");
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return null;
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isEmpty()) return null;
        return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
    }

    private void compensate(MediaStorageMetadataVo storageMetadata, String mediaId) {
        try {
            storage.destroy(storageMetadata);
        } catch (RuntimeException cleanupFailure) {
            log.error("Media upload compensation failed for mediaId={}", mediaId, cleanupFailure);
        }
    }
}
