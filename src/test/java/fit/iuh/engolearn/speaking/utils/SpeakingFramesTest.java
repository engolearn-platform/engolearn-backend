package fit.iuh.engolearn.speaking.utils;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SpeakingFramesTest {
    private static final UUID TURN = UUID.fromString("12345678-1234-4000-8000-1234567890ab");

    @Test
    void framingFollowsTheSharedProtocolAndRejectsMalformedUploads() {
        var bytes = HexFormat.of().parseHex("53504b3201000000123456781234400080001234567890ab000000090102");
        var frame = SpeakingFrames.read(ByteBuffer.wrap(bytes));
        assertThat(frame.turnId()).isEqualTo(TURN);
        assertThat(frame.index()).isEqualTo(9);
        assertThat(frame.payload()).containsExactly(1, 2);
        assertThatThrownBy(() -> SpeakingFrames.read(ByteBuffer.wrap(bytes, 0, bytes.length - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        bytes[7] = 1;
        assertThatThrownBy(() -> SpeakingFrames.read(ByteBuffer.wrap(bytes))).isInstanceOf(IllegalArgumentException.class);
        var output = SpeakingFrames.assistant(null, 0, new byte[] {82, 73, 70, 70}, 0, 4);
        assertThat(HexFormat.of().formatHex(output)).isEqualTo("53504b3202000000000000000000000000000000000000000000000052494646");
    }
}
