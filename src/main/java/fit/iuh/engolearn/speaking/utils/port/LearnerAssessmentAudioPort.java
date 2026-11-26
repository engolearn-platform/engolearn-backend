package fit.iuh.engolearn.speaking.utils.port;

import java.net.URI;
import java.time.Duration;

public interface LearnerAssessmentAudioPort {
    byte[] read(URI location, int maximumBytes, Duration timeout);
}
