package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.dto.response.AssessmentMediaResponse;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import fit.iuh.engolearn.assessment.repository.AssessmentAudioRepository;
import fit.iuh.engolearn.assessment.service.AssessmentCloudAudio;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import java.io.ByteArrayInputStream;
import fit.iuh.engolearn.assessment.utils.AssessmentWav;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssessmentCloudAudioService implements AssessmentCloudAudio {
    private final AssessmentAudioRepository repository;
    private final MediaManagement media;
    private final Clock clock;

    public AssessmentAudioAsset find(String digest) {
        if (digest == null) return null;
        var asset = repository.find(digest.toLowerCase(Locale.ROOT));
        return asset == null || asset.getMediaId() == null ? null : asset;
    }

    public AssessmentMediaResponse resolve(AssessmentAudioAsset asset, int maxSeconds) {
        validateAsset(asset, maxSeconds);
        try {
            var source = SOUND_CHECK.equals(asset.getId()) ? repository.findSourceByMediaId(asset.getMediaId()) : asset;
            validateAsset(source, maxSeconds);
            var bytes = media.readVerifiedWav(source.getMediaId(), source.getId(), AssessmentWav.MAX_BYTES);
            return new AssessmentMediaResponse("audio/wav", Base64.getEncoder().encodeToString(bytes));
        } catch (MediaNotFoundException | MediaStorageUnavailableException ex) {
            throw unavailable();
        }
    }

    @Override
    public void verifyWav(AssessmentAudioAsset asset, int maxSeconds) {
        validateAsset(asset, maxSeconds);
        try {
            media.verifyWavDelivery(asset.getMediaId(), asset.getId(), AssessmentWav.MAX_BYTES);
        } catch (MediaNotFoundException | MediaStorageUnavailableException ex) {
            throw unavailable();
        }
    }

    private static void validateAsset(AssessmentAudioAsset asset, int maxSeconds) {
        if (asset == null || asset.getDurationSeconds() <= 0 || asset.getDurationSeconds() > maxSeconds) {
            throw unavailable();
        }
    }

    /** Keeps old snapshots playable by binding their existing immutable file digest once. */
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public String upload(InputStream input, String expectedDigest, boolean soundCheck) {
        final byte[] bytes;
        try {
            bytes = input.readNBytes(AssessmentWav.MAX_BYTES + 1);
        } catch (IOException ex) {
            throw invalid("Không đọc được tệp WAV.");
        }
        if (bytes.length == 0 || bytes.length > AssessmentWav.MAX_BYTES) throw invalid("Tệp WAV vượt giới hạn 4.000.000 byte hoặc rỗng.");
        String digest = sha256(bytes);
        if (expectedDigest != null && !expectedDigest.equalsIgnoreCase(digest)) {
            throw invalid("SHA-256 của tệp WAV không khớp với dữ liệu nguồn.");
        }
        int maxSeconds = soundCheck ? 50 : 120;
        double duration = AssessmentWav.duration(bytes, maxSeconds);
        var asset = find(digest);
        if (asset == null) {
            asset = uploadNew(bytes, digest, duration, maxSeconds);
        } else {
            verifyWav(asset, maxSeconds);
        }
        if (soundCheck) {
            repository.replaceSoundCheck(new AssessmentAudioAsset(SOUND_CHECK, asset.getMediaId(), asset.getDurationSeconds()));
        }
        return digest;
    }

    private AssessmentAudioAsset uploadNew(byte[] bytes, String digest, double duration, int maxSeconds) {
        String token = UUID.randomUUID().toString();
        var now = clock.instant();
        if (!repository.reserve(digest, token, now, now.plus(Duration.ofMinutes(15)))) {
            var existing = find(digest);
            if (existing != null) {
                verifyWav(existing, maxSeconds);
                return existing;
            }
            throw new AssessmentException(409, "AUDIO_UPLOAD_PENDING", "WAV này đang tải lên. Hãy thử lại.");
        }
        final String mediaId;
        try (var content = new ByteArrayInputStream(bytes)) {
            mediaId = media.uploadVerifiedWav(new UploadMediaCommandDto("ASSESSMENT_AUDIO", MediaAccessPolicy.PROTECTED,
                    "assessment.wav", "audio/wav", bytes.length, content), digest).mediaId();
        } catch (MediaUploadRejectedException | IOException ex) {
            repository.release(digest, token);
            throw invalid("Media từ chối tệp WAV.");
        } catch (MediaStorageUnavailableException ex) {
            repository.release(digest, token);
            throw unavailable();
        }
        var asset = new AssessmentAudioAsset(digest, mediaId, duration);
        if (!repository.complete(asset, token)) throw unavailable();
        return asset;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static AssessmentException invalid(String message) {
        return new AssessmentException(422, "INVALID_ASSESSMENT_AUDIO", message);
    }

    private static AssessmentException unavailable() {
        return new AssessmentException(503, "MEDIA_UNAVAILABLE", "Âm thanh chưa sẵn sàng. Hãy thử lại.");
    }
}
