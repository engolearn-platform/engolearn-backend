package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.UploadMediaCommandDto;
import fit.iuh.engolearn.media.contract.enums.MediaAccessPolicy;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAudioMigrationResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.enums.PlacementAudioAssetStatus;
import fit.iuh.engolearn.placement.repository.PlacementAudioAssetRepository;
import fit.iuh.engolearn.placement.repository.PlacementAudioMigrationRepository;
import fit.iuh.engolearn.placement.service.PlacementAudioImportService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PlacementAudioImportServiceImpl implements PlacementAudioImportService {
    private static final int LIMIT = 25 * 1024 * 1024;
    private final MediaManagement media;
    private final PlacementAudioAssetRepository assets;
    private final PlacementAudioMigrationRepository migration;

    @Override public ItemResponse upload(String filename, long size, InputStream content) {
        if (size <= 0 || size > LIMIT) throw invalid();
        final byte[] bytes;
        final double duration;
        final String hash;
        try {
            bytes = content.readNBytes(LIMIT + 1);
            if (bytes.length != size || bytes.length > LIMIT) throw invalid();
            try (var wav = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                var f = wav.getFormat();
                if ((!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding()) && !AudioFormat.Encoding.PCM_UNSIGNED.equals(f.getEncoding()))
                        || wav.getFrameLength() <= 0 || !Float.isFinite(f.getFrameRate()) || f.getFrameRate() <= 0) throw invalid();
                duration = wav.getFrameLength() / (double) f.getFrameRate();
                if (wav.readAllBytes().length != wav.getFrameLength() * f.getFrameSize()) throw invalid();
            }
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (PlacementRequestException e) { throw e; }
        catch (Exception invalidAudio) { throw invalid(); }
        var existing = assets.findById(hash).filter(a -> a.getStatus() == PlacementAudioAssetStatus.READY);
        if (existing.isPresent()) {
            var a = existing.get();
            return new ItemResponse(media.resolve(a.getMediaId()).url().toString(), a.getDurationSeconds(), a.getTitle(), a.getMediaId());
        }
        if (!assets.reserve(hash, filename, duration)) throw new PlacementRequestException(HttpStatus.CONFLICT,
                "PLACEMENT_AUDIO_UPLOAD_IN_PROGRESS", "This audio has an unfinished upload; review its import journal before retrying.");
        final fit.iuh.engolearn.media.contract.dto.MediaUploadResultDto uploaded;
        try (var stream = new ByteArrayInputStream(bytes)) {
            // Audio was already public for Guest Placement; preserve that policy. Only Admin can upload/link it.
            uploaded = media.upload(new UploadMediaCommandDto("PLACEMENT_LISTENING", MediaAccessPolicy.PUBLIC,
                    filename, "audio/wav", bytes.length, stream));
        } catch (RuntimeException failure) { assets.fail(hash); throw failure; }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        // An unknown journal write must remain UPLOADING for operator reconciliation, never auto-reupload.
        try { assets.complete(hash, uploaded.mediaId()); }
        catch (RuntimeException uncertainWrite) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Placement upload journal reconciliation required: hash={}, mediaId={}", hash, uploaded.mediaId());
            throw uncertainWrite;
        }
        return new ItemResponse(media.resolve(uploaded.mediaId()).url().toString(), duration, filename, uploaded.mediaId());
    }
    @Override public PlacementAudioMigrationResponse preflight() {
        var missing = migration.requiredHashes().stream().filter(hash -> assets.findById(hash)
                .filter(a -> a.getStatus() == PlacementAudioAssetStatus.READY).isEmpty()).toList();
        return new PlacementAudioMigrationResponse(missing, 0);
    }
    @Override public PlacementAudioMigrationResponse linkImported() {
        long changed = 0;
        for (var a : assets.findAll()) changed += migration.link(a.getId(), a.getMediaId());
        return new PlacementAudioMigrationResponse(preflight().missingHashes(), changed);
    }
    private PlacementRequestException invalid() {
        return new PlacementRequestException(HttpStatus.BAD_REQUEST, "PLACEMENT_AUDIO_INVALID", "Upload a complete PCM WAV, at most 25 MiB.");
    }
}
