package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort.Failure;
import fit.iuh.engolearn.speaking.utils.port.LearnerAssessmentAudioPort;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

@Component
public class SpeakingProtectedAudioReader implements LearnerAssessmentAudioPort {
    @Override
    public byte[] read(URI location, int maximumBytes, Duration timeout) {
        if (location == null || !"https".equalsIgnoreCase(location.getScheme())) throw unavailable();
        HttpURLConnection connection = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            connection = (HttpURLConnection) location.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            int requestMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeout.toMillis()));
            connection.setConnectTimeout(requestMs);
            connection.setReadTimeout(Math.min(requestMs, 1000));
            if (connection.getResponseCode() != 200) throw unavailable();
            if (connection.getContentLengthLong() > maximumBytes) throw unsupportedLength();
            try (var input = connection.getInputStream(); var bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (System.nanoTime() > deadline) throw new Failure("AUDIO_RECOVERY_TIMEOUT", true);
                    if (bytes.size() + count > maximumBytes) throw unsupportedLength();
                    bytes.write(chunk, 0, count);
                }
                return bytes.toByteArray();
            }
        } catch (IOException unavailable) {
            throw unavailable();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Failure unavailable() {
        return new Failure("AUDIO_MEDIA_UNAVAILABLE", true);
    }

    private static Failure unsupportedLength() {
        return new Failure("AUDIO_LENGTH_UNSUPPORTED", false);
    }
}
