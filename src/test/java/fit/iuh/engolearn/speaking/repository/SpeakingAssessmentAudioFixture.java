package fit.iuh.engolearn.speaking.repository;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class SpeakingAssessmentAudioFixture {
    private SpeakingAssessmentAudioFixture() {}

    static byte[] voicedPcm() {
        return voicedPcm(9_000);
    }

    static byte[] voicedPcm(int durationMs) {
        var pcm = ByteBuffer.allocate(durationMs * 32).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < durationMs * 16; i++) {
            pcm.putShort((short) (3500 * (0.7 + 0.3 * Math.sin(i * 2 * Math.PI * 4 / 16000))
                    * Math.sin(i * 2 * Math.PI * 200 / 16000)));
        }
        return pcm.array();
    }
}
