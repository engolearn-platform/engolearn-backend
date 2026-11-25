package fit.iuh.engolearn.speaking.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class SpeakingWav {
    public static final int PCM_BYTES_PER_MS = 32;
    public static final int MAX_REPLY_BYTES = 6 * 1024 * 1024;
    static final int PCM_HEADER_BYTES = 44;
    private static final int RIFF = 0x46464952;
    private static final int WAVE = 0x45564157;
    private static final int FMT = 0x20746d66;
    private static final int DATA = 0x61746164;

    private SpeakingWav() {}

    public static byte[] learner(byte[] pcm) {
        return pcm16Mono(pcm, 16_000);
    }

    static byte[] pcm16Mono(byte[] pcm, int sampleRate) {
        return ByteBuffer.allocate(PCM_HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(RIFF).putInt(36 + pcm.length).putInt(WAVE).putInt(FMT).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * Short.BYTES)
                .putShort((short) 2).putShort((short) 16).putInt(DATA).putInt(pcm.length).put(pcm).array();
    }

    public static byte[] learnerPcm(byte[] wav, int maximumBytes) {
        if (wav == null || wav.length < 44 || wav.length - 44 > maximumBytes
                || (wav.length & 1) != 0) throw invalid();
        var format = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (format.getInt() != RIFF || format.getInt() != wav.length - 8 || format.getInt() != WAVE
                || format.getInt() != FMT || format.getInt() != 16 || format.getShort() != 1
                || format.getShort() != 1 || format.getInt() != 16000 || format.getInt() != 32000
                || format.getShort() != 2 || format.getShort() != 16 || format.getInt() != DATA
                || format.getInt() != wav.length - 44) throw invalid();
        return Arrays.copyOfRange(wav, 44, wav.length);
    }

    public static boolean containsSignal(byte[] pcm) {
        var samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        // Gate only digital silence. STT determines whether other audio contains meaningful speech.
        while (samples.remaining() >= 2) if (samples.getShort() != 0) return true;
        return false;
    }

    public static long durationMs(byte[] wav) {
        if (wav == null || wav.length < 44 || wav.length > MAX_REPLY_BYTES) throw invalid();
        ByteBuffer bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.getInt() != RIFF || bytes.getInt() != wav.length - 8 || bytes.getInt() != WAVE) throw invalid();
        int byteRate = 0;
        int dataSize = 0;
        while (bytes.remaining() >= 8) {
            int chunk = bytes.getInt();
            int size = bytes.getInt();
            int start = bytes.position();
            if (size < 0 || size > bytes.remaining() || (size & 1) > bytes.remaining() - size) throw invalid();
            if (chunk == FMT) {
                if (size < 16 || bytes.getShort() != 1 || bytes.getShort() != 1) throw invalid();
                int sampleRate = bytes.getInt();
                byteRate = bytes.getInt();
                if (sampleRate < 8000 || sampleRate > 96000 || byteRate != sampleRate * 2
                        || bytes.getShort() != 2 || bytes.getShort() != 16) throw invalid();
            } else if (chunk == DATA) {
                if (size == 0 || size % 2 != 0) throw invalid();
                dataSize = size;
            }
            bytes.position(start + size + (size & 1));
        }
        if (byteRate == 0 || dataSize == 0 || bytes.hasRemaining()) throw invalid();
        return Math.max(1, (dataSize * 1000L + byteRate - 1) / byteRate);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid PCM WAV audio");
    }
}
