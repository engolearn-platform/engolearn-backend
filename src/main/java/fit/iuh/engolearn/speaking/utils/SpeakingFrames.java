package fit.iuh.engolearn.speaking.utils;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class SpeakingFrames {
    public static final int HEADER = 28;
    public static final int CHUNK = 8192;
    public static final int MAX_TEXT = 16 * 1024;
    private static final int MAGIC = 0x53504b32;

    private SpeakingFrames() {}

    public record Learner(UUID turnId, long index, byte[] payload) {}

    public static Learner read(ByteBuffer input) {
        ByteBuffer frame = input.slice();
        if (frame.remaining() <= HEADER || frame.remaining() > HEADER + CHUNK
                || (frame.remaining() - HEADER) % 2 != 0
                || frame.getInt() != MAGIC || frame.getInt() != 0x01000000) {
            throw new IllegalArgumentException("Invalid learner audio frame");
        }
        UUID turn = new UUID(frame.getLong(), frame.getLong());
        long index = Integer.toUnsignedLong(frame.getInt());
        byte[] pcm = new byte[frame.remaining()];
        frame.get(pcm);
        return new Learner(turn, index, pcm);
    }

    public static byte[] assistant(UUID turn, int index, byte[] wav, int from, int length) {
        return ByteBuffer.allocate(HEADER + length).putInt(MAGIC).putInt(0x02000000)
                .putLong(turn == null ? 0 : turn.getMostSignificantBits())
                .putLong(turn == null ? 0 : turn.getLeastSignificantBits())
                .putInt(index).put(wav, from, length).array();
    }
}
