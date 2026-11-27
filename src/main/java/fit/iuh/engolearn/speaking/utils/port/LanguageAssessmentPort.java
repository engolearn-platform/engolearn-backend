package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.utils.SpeakingConversation.Message;

import java.time.Duration;
import java.util.List;

public interface LanguageAssessmentPort {
    String assess(String system, String data, List<Message> input, String schema, Duration timeout);

    class Failure extends RuntimeException {
        public Failure(String reason) {
            super(reason);
        }
    }
}
