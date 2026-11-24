package fit.iuh.engolearn.placement.infra;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse.ItemResponse;
import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionProvenanceVo;
import fit.iuh.engolearn.placement.service.PlacementDevelopmentAudio;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("dev & !prod")
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementWavMedia implements PlacementDevelopmentAudio {
    private static final String ROUTE = "/api/v1/placement/media/";
    private static final Pattern FILE_NAME = Pattern.compile("([a-f0-9]{64})\\.wav");
    private static final double SEED_DURATION_TOLERANCE = 0.001;
    private final Path directory;

    public PlacementWavMedia(
            @Value("${engolearn.placement.media-directory:./tools/mongo-json-importer/samples/placement-media}")
            String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String resolve(PlacementQuestion question) {
        if (question.getSkill() != PlacementSkill.LISTENING) return null;
        PlacementQuestionProvenanceVo provenance = question.getProvenance();
        boolean selected = question.isAdminAudioOverride();
        String url = question.getAudioUrl();
        Double duration = question.getAudioDurationSeconds();
        if (!selected) {
            if (provenance == null || provenance.getAudioSha256() == null) return null;
            String provenanceUrl = ROUTE + provenance.getAudioSha256() + ".wav";
            if (url != null && !url.isBlank() && !url.equals(provenanceUrl)) return null;
            url = provenanceUrl;
            duration = provenance.getAudioDurationSeconds();
        }
        if (duration == null || !Double.isFinite(duration) || duration <= 0) return null;
        ItemResponse item = inspectUrl(url);
        return item != null && Math.abs(item.durationSeconds() - duration) <= SEED_DURATION_TOLERANCE
                ? item.audioUrl() : null;
    }

    @Override
    public void requirePlayable(PlacementQuestion question) {
        if (question.getSkill() != PlacementSkill.LISTENING) return;
        if (question.getTranscript() == null || question.getTranscript().isBlank()) {
            throw new PlacementQuestionValidationException(Map.of("transcript", "A Listening question needs a transcript."));
        }
        if (resolve(question) == null) {
            throw new PlacementQuestionValidationException(Map.of("audioUrl", "Select a verified Placement WAV."));
        }
    }

    @Override
    public ItemResponse requireSelection(String url) {
        ItemResponse item = inspectUrl(url);
        if (item == null) {
            throw new PlacementQuestionValidationException(Map.of("audioUrl", "Select a verified Placement WAV."));
        }
        return item;
    }

    @Override
    public PlacementAdminQuestionMediaResponse catalog(Map<String, String> titlesByHash) {
        if (!Files.isDirectory(directory)) return new PlacementAdminQuestionMediaResponse(List.of());
        try (var entries = Files.list(directory)) {
            return new PlacementAdminQuestionMediaResponse(entries
                    .map(path -> path.getFileName().toString())
                    .filter(name -> FILE_NAME.matcher(name).matches())
                    .sorted()
                    .map(name -> inspect(name, titlesByHash.getOrDefault(name.substring(0, 64), "")))
                    .filter(Objects::nonNull)
                    .toList());
        } catch (IOException failure) {
            throw new PlacementRequestException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PLACEMENT_MEDIA_UNAVAILABLE", "The Placement media catalog could not be opened.");
        }
    }

    private ItemResponse inspectUrl(String url) {
        return url == null || !url.startsWith(ROUTE) ? null : inspect(url.substring(ROUTE.length()), "");
    }

    private ItemResponse inspect(String name, String title) {
        var filename = FILE_NAME.matcher(name);
        if (!filename.matches()) return null;
        try {
            Path file = PlacementMediaResourceResolver.findFile(directory, name);
            if (file == null || !filename.group(1).equals(sha256(file))
                    || !AudioFileFormat.Type.WAVE.equals(AudioSystem.getAudioFileFormat(file.toFile()).getType())) {
                return null;
            }
            try (var wav = AudioSystem.getAudioInputStream(file.toFile())) {
                var format = wav.getFormat();
                if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                        && !AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding())) return null;
                long frames = wav.getFrameLength();
                int frameSize = format.getFrameSize();
                float frameRate = format.getFrameRate();
                if (frames <= 0 || frameSize <= 0 || frameSize > 8192
                        || !Float.isFinite(frameRate) || frameRate <= 0) return null;
                byte[] buffer = new byte[8192];
                long remaining = frames * frameSize;
                while (remaining > 0) {
                    int read = wav.read(buffer);
                    if (read <= 0) return null;
                    remaining -= read;
                }
                return remaining == 0 ? new ItemResponse(ROUTE + name, frames / (double) frameRate, title) : null;
            }
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException failure) {
            return null;
        }
    }

    private String sha256(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = stream.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 provider missing", impossible);
        }
    }
}
