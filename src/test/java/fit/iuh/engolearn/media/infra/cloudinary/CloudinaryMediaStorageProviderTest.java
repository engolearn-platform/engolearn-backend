package fit.iuh.engolearn.media.infra.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.model.enums.MediaDeliveryType;
import fit.iuh.engolearn.media.model.enums.MediaResourceType;
import fit.iuh.engolearn.media.utils.MediaFileDescriptor;
import fit.iuh.engolearn.shared.media.CloudinaryMediaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class CloudinaryMediaStorageProviderTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryMediaStorageProvider provider;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        provider = new CloudinaryMediaStorageProvider(new CloudinaryMediaClient(cloudinary, true));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadsPublicImageUsingStreamCapableSdkMethod() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47};
        InputStream stream = new ByteArrayInputStream(png);
        when(uploader.uploadLarge(any(InputStream.class), anyMap())).thenAnswer(call -> {
            assertThat(((InputStream) call.getArgument(0)).readAllBytes()).isEqualTo(png);
            return Map.of(
                    "asset_id", "cloudinary-asset-id",
                    "public_id", "engolearn/media/test-image",
                    "format", "png",
                    "version", 123L);
        });

        var result = provider.upload("engolearn/media/test-image",
                new MediaFileDescriptor("image/png", "png", MediaResourceType.IMAGE),
                MediaAccessPolicy.PUBLIC, stream);

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).uploadLarge(same(stream), options.capture());
        verify(uploader, never()).upload(any(), anyMap());
        assertThat(options.getValue()).containsEntry("resource_type", "image")
                .containsEntry("type", "upload")
                .containsEntry("public_id", "engolearn/media/test-image")
                .containsEntry("filename", "media.png")
                .containsEntry("format", "png")
                .containsEntry("overwrite", false);
        assertThat(result.getAssetId()).isEqualTo("cloudinary-asset-id");
        assertThat(result.getDeliveryType()).isEqualTo(MediaDeliveryType.UPLOAD);
        assertThat(result.getPublicId()).isEqualTo("engolearn/media/test-image");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadsProtectedAudioUsingStreamCapableSdkMethod() throws Exception {
        byte[] mp3 = {'I', 'D', '3', 3, 0, 0};
        InputStream stream = new ByteArrayInputStream(mp3);
        when(uploader.uploadLarge(any(InputStream.class), anyMap())).thenAnswer(call -> {
            assertThat(((InputStream) call.getArgument(0)).readAllBytes()).isEqualTo(mp3);
            return Map.of("asset_id", "audio-asset", "public_id", "engolearn/media/test-audio",
                    "format", "mp3", "version", 456L);
        });

        var result = provider.upload("engolearn/media/test-audio",
                new MediaFileDescriptor("audio/mpeg", "mp3", MediaResourceType.VIDEO),
                MediaAccessPolicy.PROTECTED, stream);

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).uploadLarge(same(stream), options.capture());
        verify(uploader, never()).upload(any(), anyMap());
        assertThat(options.getValue()).containsEntry("resource_type", "video")
                .containsEntry("type", "authenticated")
                .containsEntry("filename", "media.mp3")
                .containsEntry("overwrite", false);
        assertThat(result.getDeliveryType()).isEqualTo(MediaDeliveryType.AUTHENTICATED);
        assertThat(result.getResourceType()).isEqualTo(MediaResourceType.VIDEO);
        assertThat(result.getVersion()).isEqualTo(456L);
    }
}
