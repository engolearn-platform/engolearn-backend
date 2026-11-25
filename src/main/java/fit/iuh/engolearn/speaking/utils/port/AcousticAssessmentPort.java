package fit.iuh.engolearn.speaking.utils.port;

import fit.iuh.engolearn.speaking.model.vo.SpeakingWordAssessmentVo;

import java.time.Duration;
import java.util.List;

public interface AcousticAssessmentPort {
    Result assess(byte[] pcm, Duration timeout);

    record Result(Double pronunciation, Double fluency, int words, List<SpeakingWordAssessmentVo> feedback,
                  String pronunciationReason, String fluencyReason, long assessedSpeechDurationMs,
                  long fluencyEvidenceDurationMs) {
        public Result(Double pronunciation, Double fluency, int words, List<SpeakingWordAssessmentVo> feedback,
                      long assessedSpeechDurationMs, long fluencyEvidenceDurationMs) {
            this(pronunciation, fluency, words, feedback, null, null, assessedSpeechDurationMs, fluencyEvidenceDurationMs);
        }
    }

    class Failure extends RuntimeException {
        private final boolean transientFailure;

        public Failure(String reason, boolean transientFailure) {
            super(reason);
            this.transientFailure = transientFailure;
        }

        public boolean transientFailure() {
            return transientFailure;
        }
    }
}
