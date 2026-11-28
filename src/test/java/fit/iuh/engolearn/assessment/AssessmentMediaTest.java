package fit.iuh.engolearn.assessment;

import fit.iuh.engolearn.assessment.service.impl.AssessmentMediaImpl;
import fit.iuh.engolearn.assessment.service.impl.AssessmentCloudAudioService;
import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import fit.iuh.engolearn.assessment.repository.AssessmentAudioRepository;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.sound.sampled.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class AssessmentMediaTest {
    @TempDir Path directory;
    private AssessmentMediaImpl localMedia() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        return new AssessmentMediaImpl(directory.toString(), environment, null);
    }
    private String wave(String file,int seconds) throws Exception {
        Path path=directory.resolve(file);var format=new AudioFormat(16000,16,1,true,false);
        try(var input=new AudioInputStream(new ByteArrayInputStream(new byte[seconds*32000]),format,seconds*16000L)) {AudioSystem.write(input,AudioFileFormat.Type.WAVE,path.toFile());}
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    @Test void validHashAndDurationLoadTheOriginalBytes() throws Exception {
        var digest=wave("audio.wav",1);var media=localMedia();
        assertThat(media.read("audio.wav",digest,50)).isEqualTo(Files.readAllBytes(directory.resolve("audio.wav")));
    }
    @Test void changedResourceFailsInsteadOfReplacingImmutableAudio() throws Exception {
        wave("audio.wav",1);assertThatThrownBy(()->localMedia().read("audio.wav","0".repeat(64),50)).isInstanceOf(AssessmentException.class);
    }
    @Test void singleAudioOverFiftySecondsFailsButPassageMayUseIt() throws Exception {
        var digest=wave("audio.wav",51);var media=localMedia();
        assertThatThrownBy(()->media.read("audio.wav",digest,50)).isInstanceOf(AssessmentException.class);
        assertThat(media.read("audio.wav",digest,120)).isNotEmpty();
    }
    @Test
    void localMediaRejectsRenamedAiffAndTruncatedWave() throws Exception {
        var renamed = directory.resolve("renamed.wav");
        var format = new AudioFormat(16000, 16, 1, true, false);
        try (var input = new AudioInputStream(new ByteArrayInputStream(new byte[32000]), format, 16000)) {
            AudioSystem.write(input, AudioFileFormat.Type.AIFF, renamed.toFile());
        }
        var local = localMedia();
        assertThatThrownBy(() -> local.read("renamed.wav", null, 28)).isInstanceOf(AssessmentException.class);
        wave("truncated.wav", 1);
        var path = directory.resolve("truncated.wav");
        Files.write(path, java.util.Arrays.copyOf(Files.readAllBytes(path), 64));
        assertThatThrownBy(() -> local.read("truncated.wav", null, 28)).isInstanceOf(AssessmentException.class);
    }

    @Test void filesystemTraversalCannotReadOutsideConfiguredMediaRoot() {
        assertThatThrownBy(()->localMedia().read("../outside.wav",null,50)).isInstanceOf(AssessmentException.class);
    }

    @Test void productionRejectsLocalMediaEvenWhenDirectoryIsProvided() throws Exception {
        var digest=wave("audio.wav",1);
        var environment=new MockEnvironment();environment.setActiveProfiles("prod");
        var media=new AssessmentMediaImpl(directory.toString(),environment,null);
        assertThatThrownBy(()->media.read("audio.wav",digest,50))
                .isInstanceOfSatisfying(AssessmentException.class,error->{
                    assertThat(error.status()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("MEDIA_UNAVAILABLE");
                });
    }

    @Test
    void productionResolvesStoredAndLegacyReferencesWithoutFiles() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var assetRepo = mock(AssessmentAudioRepository.class);
        var cloud = mock(MediaManagement.class);
        var provider = new AssessmentCloudAudioService(assetRepo, cloud, Clock.systemUTC());
        var media = new AssessmentMediaImpl(directory.toString(), environment, provider);
        var hash = "a".repeat(64);
        var legacy = AssessmentUnitVo.builder().audioPath("missing.wav").audioSha256(hash).build();
        var asset = new AssessmentAudioAsset(hash, "media-id", 27);
        var expiresAt = Instant.parse("2026-11-23T00:00:00Z");
        var url = URI.create("https://media.example.test/private.wav");
        when(assetRepo.find(hash)).thenReturn(asset);
        when(cloud.resolve("media-id")).thenReturn(new MediaDeliveryDto(url, expiresAt, "audio/wav"));
        assertThat(media.delivery(legacy, 28).url()).isEqualTo(url);
        assertThat(media.delivery(legacy, 28).expiresAt()).isEqualTo(expiresAt);
        assertThat(media.durableReference(legacy)).isEqualTo("media-id");
        legacy.setAudioMediaId("media-id");
        var renewed = new MediaDeliveryDto(URI.create("https://media.example.test/renewed.wav"),
                expiresAt.plusSeconds(60), "audio/wav");
        when(cloud.resolve("media-id")).thenReturn(renewed);
        var replay = media.delivery(legacy, 28);
        assertThat(replay.base64()).isNull();
        assertThat(replay.url()).isEqualTo(renewed.url());
        assertThat(replay.expiresAt()).isEqualTo(renewed.expiresAt());
        legacy.setAudioMediaId("other");
        assertThatThrownBy(() -> media.delivery(legacy, 28)).isInstanceOf(AssessmentException.class);
        legacy.setAudioMediaId("media-id");
        assertThatThrownBy(() -> media.delivery(legacy, 26)).isInstanceOf(AssessmentException.class);
    }

    @Test
    void uploadChecksDigestAndBindsProtectedWavOnce() throws Exception {
        var digest = wave("cloud.wav", 1);
        var assetRepo = mock(AssessmentAudioRepository.class);
        var cloud = mock(MediaManagement.class);
        var provider = new AssessmentCloudAudioService(assetRepo, cloud, Clock.systemUTC());
        var path = directory.resolve("cloud.wav");
        try (var input = Files.newInputStream(path)) {
            assertThatThrownBy(() -> provider.upload(input, "0".repeat(64), false)).isInstanceOf(AssessmentException.class);
        }
        verifyNoInteractions(assetRepo, cloud);
        when(assetRepo.reserve(eq(digest), any(), any(), any())).thenReturn(true);
        when(assetRepo.complete(any(), any())).thenReturn(true);
        when(cloud.uploadVerifiedWav(any(), eq(digest))).thenReturn(new MediaUploadResultDto("media-id", "audio/wav", Files.size(path),
                MediaAccessPolicy.PROTECTED));
        try (var input = Files.newInputStream(path)) {
            assertThat(provider.upload(input, digest, false)).isEqualTo(digest);
        }
        when(assetRepo.find(digest)).thenReturn(new AssessmentAudioAsset(digest, "media-id", 1));
        try (var input = Files.newInputStream(path)) {
            assertThat(provider.upload(input, digest, false)).isEqualTo(digest);
        }
        var command = ArgumentCaptor.forClass(UploadMediaCommandDto.class);
        verify(cloud).uploadVerifiedWav(command.capture(), eq(digest));
        verify(cloud).verifyWavDelivery(eq("media-id"), eq(digest), anyLong());
        assertThat(command.getValue().accessPolicy()).isEqualTo(MediaAccessPolicy.PROTECTED);
        assertThat(command.getValue().sizeBytes()).isEqualTo(Files.size(path));
    }
}
