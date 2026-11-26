package fit.iuh.engolearn.assessment;

import fit.iuh.engolearn.assessment.service.impl.AssessmentMediaImpl;
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
        return new AssessmentMediaImpl(directory.toString(), new MockEnvironment());
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
    @Test void filesystemTraversalCannotReadOutsideConfiguredMediaRoot() {
        assertThatThrownBy(()->localMedia().read("../outside.wav",null,50)).isInstanceOf(AssessmentException.class);
    }

    @Test void productionRejectsLocalMediaEvenWhenDirectoryIsProvided() throws Exception {
        var digest=wave("audio.wav",1);
        var environment=new MockEnvironment();environment.setActiveProfiles("prod");
        var media=new AssessmentMediaImpl(directory.toString(),environment);
        assertThatThrownBy(()->media.read("audio.wav",digest,50))
                .isInstanceOfSatisfying(AssessmentException.class,error->{
                    assertThat(error.status()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("MEDIA_UNAVAILABLE");
                });
    }
}
