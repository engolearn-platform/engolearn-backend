package fit.iuh.engolearn.placement.infra;

import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionProvenanceVo;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementAdminQuestionMediaTest {
    @TempDir Path directory;
    private PlacementWavMedia media;
    private Path file;
    private String hash;

    @BeforeEach
    void setup() throws Exception {
        var source = directory.resolve("source.wav");
        byte[] frames = new byte[16000];
        try (var stream = new AudioInputStream(new ByteArrayInputStream(frames),
                new AudioFormat(8000, 16, 1, true, false), 8000)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, source.toFile());
        }
        hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        file = Files.move(source, directory.resolve(hash + ".wav"));
        media = new PlacementWavMedia(directory.toString());
    }

    @Test
    void resolvesSeedAudioWithoutRewritingItsStoredUrl() {
        var question = seed(1.0);
        assertThat(media.resolve(question)).isEqualTo("/api/v1/placement/media/" + hash + ".wav");
        assertThat(question.getAudioUrl()).isEmpty();
        media.requirePlayable(question);
        assertThat(media.catalog(Map.of(hash, "A1 store hours")).items()).singleElement()
                .satisfies(item -> assertThat(item.title()).isEqualTo("A1 store hours"));
    }

    @Test
    void rejectsMismatchedSeedDurationAndModifiedAudio() throws Exception {
        assertThat(media.resolve(seed(1.2))).isNull();
        Files.writeString(file, "different content");
        assertThat(media.resolve(seed(1.0))).isNull();
        assertThatThrownBy(() -> media.requirePlayable(seed(1.0)))
                .isInstanceOf(PlacementQuestionValidationException.class);
    }

    @Test
    void usesTheSelectedAudioDurationWithoutChangingSeedProvenance() {
        var question = seed(1.2);
        question.setAudioUrl("/api/v1/placement/media/" + hash + ".wav");
        question.setAdminAudioOverride(true);
        question.setAudioDurationSeconds(1.0);
        media.requirePlayable(question);
        assertThat(question.getProvenance().getAudioDurationSeconds()).isEqualTo(1.2);
    }

    private PlacementQuestion seed(double duration) {
        return PlacementQuestion.builder().skill(PlacementSkill.LISTENING).audioUrl("").transcript("Hello")
                .provenance(PlacementQuestionProvenanceVo.builder()
                        .audioSha256(hash).audioDurationSeconds(duration).build()).build();
    }
}
