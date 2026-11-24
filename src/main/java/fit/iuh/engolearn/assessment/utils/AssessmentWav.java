package fit.iuh.engolearn.assessment.utils;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AssessmentWav {
    public static final int MAX_BYTES = 4_000_000;

    private AssessmentWav() {}

    public static double duration(byte[] bytes, int limit) {
        try (var content = new ByteArrayInputStream(bytes);
             var audio = AudioSystem.getAudioInputStream(content)) {
            var fileType = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(bytes)).getType();
            var encoding = audio.getFormat().getEncoding();
            double seconds = audio.getFrameLength() / (double) audio.getFormat().getFrameRate();
            long expectedBytes = Math.multiplyExact(audio.getFrameLength(), (long) audio.getFormat().getFrameSize());
            if (!AudioFileFormat.Type.WAVE.equals(fileType)
                    || !AudioFormat.Encoding.PCM_SIGNED.equals(encoding) && !AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding)
                    || !Double.isFinite(seconds) || seconds <= 0 || seconds > limit
                    || expectedBytes <= 0 || audio.readAllBytes().length != expectedBytes) {
                throw invalid("Cần WAV PCM đầy đủ và có thời lượng tối đa " + limit + " giây.");
            }
            return seconds;
        } catch (UnsupportedAudioFileException | IOException | ArithmeticException ex) {
            throw invalid("Không nhận diện được WAV PCM hợp lệ.");
        }
    }

    private static AssessmentException invalid(String message) {
        return new AssessmentException(422, "INVALID_ASSESSMENT_AUDIO", message);
    }
}
