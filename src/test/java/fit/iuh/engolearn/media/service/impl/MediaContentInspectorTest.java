package fit.iuh.engolearn.media.service.impl;

import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.utils.MediaPayloadTooLargeIOException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaContentInspectorTest {

    @Test
    void detectsPngAndReplaysTheInspectedSignatureToTheProvider() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var inspected = MediaContentInspector.inspect(new ByteArrayInputStream(png), "image/png", png.length, 1024);

        assertThat(inspected.descriptor().contentType()).isEqualTo("image/png");
        assertThat(inspected.descriptor().format()).isEqualTo("png");
        assertThat(inspected.content().readAllBytes()).containsExactly(png);
        assertThat(inspected.bytesRead()).isEqualTo(png.length);
    }

    @Test
    void detectsAudioAsCloudinaryVideoResource() {
        byte[] wav = new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        var inspected = MediaContentInspector.inspect(new ByteArrayInputStream(wav), "audio/x-wav", wav.length, 1024);

        assertThat(inspected.descriptor().contentType()).isEqualTo("audio/wav");
        assertThat(inspected.descriptor().format()).isEqualTo("wav");
        assertThat(inspected.descriptor().resourceType().cloudinaryValue()).isEqualTo("video");
    }

    @Test
    void rejectsDeclaredMimeTypeThatDoesNotMatchTheFileSignature() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThatThrownBy(() -> MediaContentInspector.inspect(
                new ByteArrayInputStream(png), "image/jpeg", png.length, 1024))
                .isInstanceOf(MediaUploadRejectedException.class);
    }

    @Test
    void rejectsUnknownContentAndDeclaredSizeAboveTheConfiguredLimit() {
        byte[] unknown = "not a media file".getBytes();
        assertThatThrownBy(() -> MediaContentInspector.inspect(
                new ByteArrayInputStream(unknown), "application/octet-stream", unknown.length, 1024))
                .isInstanceOf(MediaUploadRejectedException.class);
        assertThatThrownBy(() -> MediaContentInspector.inspect(
                new ByteArrayInputStream(unknown), "application/octet-stream", 1025, 1024))
                .isInstanceOf(MediaUploadRejectedException.class);
    }

    @Test
    void stopsReadingWhenTheActualStreamExceedsTheGlobalLimit() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1};
        var inspected = MediaContentInspector.inspect(new ByteArrayInputStream(png), "image/png", 12, 12);

        assertThatThrownBy(() -> inspected.content().readAllBytes())
                .isInstanceOf(MediaPayloadTooLargeIOException.class);
    }
}
