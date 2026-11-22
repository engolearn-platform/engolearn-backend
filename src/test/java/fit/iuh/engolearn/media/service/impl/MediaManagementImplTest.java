package fit.iuh.engolearn.media.service.impl;

import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.model.MediaAsset;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaProvider;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.model.vo.MediaStorageMetadataVo;
import fit.iuh.engolearn.media.repository.MediaAssetRepository;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.media.utils.MediaStorageProvider;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediaManagementImplTest {

    private MediaAssetRepository repository;
    private MediaStorageProvider storage;
    private CurrentUserProvider currentUserProvider;
    private MediaManagementImpl media;

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        storage = mock(MediaStorageProvider.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        media = new MediaManagementImpl(repository, storage, currentUserProvider,
                1024 * 1024, 512 * 1024, Duration.ofSeconds(60));
    }

    @Test
    void uploadsPublicMediaAndReturnsOnlyTheInternalReference() throws Exception {
        byte[] png = png();
        MediaStorageMetadataVo stored = stored(MediaDeliveryType.UPLOAD, MediaResourceType.IMAGE);
        when(storage.upload(anyString(), any(MediaFileDescriptor.class), eq(MediaAccessPolicy.PUBLIC), any()))
                .thenAnswer(invocation -> {
                    InputStream content = invocation.getArgument(3);
                    content.readAllBytes();
                    return stored;
                });

        var result = media.upload(new UploadMediaCommandDto(
                "TOPIC_IMAGE", MediaAccessPolicy.PUBLIC, "cover.png", "image/png",
                png.length, new ByteArrayInputStream(png)));

        assertThat(result.mediaId()).isNotBlank();
        assertThat(result.accessPolicy()).isEqualTo(MediaAccessPolicy.PUBLIC);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.sizeBytes()).isEqualTo(png.length);
        verify(repository).save(any(MediaAsset.class));
        verify(storage, never()).destroy(any());
    }

    @Test
    void resolvesPublicMediaWithoutAnExpiry() {
        MediaAsset asset = MediaAsset.builder().id("media-public").accessPolicy(MediaAccessPolicy.PUBLIC)
                .contentType("image/png").storage(stored(MediaDeliveryType.UPLOAD, MediaResourceType.IMAGE)).build();
        when(repository.findById("media-public")).thenReturn(Optional.of(asset));
        when(storage.publicUrl(asset.getStorage())).thenReturn(URI.create("https://cdn.example/image.png"));

        var delivery = media.resolve("media-public");

        assertThat(delivery.url()).isEqualTo(URI.create("https://cdn.example/image.png"));
        assertThat(delivery.expiresAt()).isNull();
        assertThat(delivery.contentType()).isEqualTo("image/png");
        verify(storage, never()).privateDownloadUrl(any(), any());
    }

    @Test
    void resolvesProtectedMediaWithTheConfiguredExpiry() {
        MediaAsset asset = MediaAsset.builder().id("media-protected").accessPolicy(MediaAccessPolicy.PROTECTED)
                .contentType("audio/mpeg").storage(stored(MediaDeliveryType.AUTHENTICATED, MediaResourceType.VIDEO)).build();
        when(repository.findById("media-protected")).thenReturn(Optional.of(asset));
        when(storage.privateDownloadUrl(eq(asset.getStorage()), any(Instant.class)))
                .thenReturn(URI.create("https://api.cloudinary.com/private-audio"));

        var delivery = media.resolve("media-protected");

        assertThat(delivery.url()).isEqualTo(URI.create("https://api.cloudinary.com/private-audio"));
        assertThat(delivery.expiresAt()).isNotNull();
        assertThat(delivery.contentType()).isEqualTo("audio/mpeg");
        verify(storage).privateDownloadUrl(eq(asset.getStorage()), any(Instant.class));
    }

    @Test
    void compensatesCloudinaryWhenMongoPersistenceFails() throws Exception {
        byte[] png = png();
        MediaStorageMetadataVo stored = stored(MediaDeliveryType.UPLOAD, MediaResourceType.IMAGE);
        when(storage.upload(anyString(), any(MediaFileDescriptor.class), eq(MediaAccessPolicy.PUBLIC), any()))
                .thenAnswer(invocation -> {
                    InputStream content = invocation.getArgument(3);
                    content.readAllBytes();
                    return stored;
                });
        when(repository.save(any(MediaAsset.class))).thenThrow(new IllegalStateException("mongo unavailable"));

        assertThatThrownBy(() -> media.upload(new UploadMediaCommandDto(
                "TOPIC_IMAGE", MediaAccessPolicy.PUBLIC, "cover.png", "image/png",
                png.length, new ByteArrayInputStream(png))))
                .isInstanceOf(MediaStorageUnavailableException.class);

        verify(storage).destroy(stored);
    }

    @Test
    void rejectsUnknownMediaIds() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> media.resolve("missing")).isInstanceOf(MediaNotFoundException.class);
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static MediaStorageMetadataVo stored(MediaDeliveryType delivery, MediaResourceType resource) {
        return MediaStorageMetadataVo.builder().provider(MediaProvider.CLOUDINARY).assetId("cloudinary-asset")
                .publicId("engolearn/media/test").resourceType(resource).deliveryType(delivery)
                .format(resource == MediaResourceType.IMAGE ? "png" : "mp3").version(123L).build();
    }
}
