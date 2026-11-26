package fit.iuh.engolearn.speaking.service;

/** Internal handoff; preserve the original recording including its pauses. */
public record FinalizedSpeakingAudio(String sessionId, String turnId, int sequence, String mediaId, byte[] pcm) {
    public FinalizedSpeakingAudio {
        pcm = pcm.clone();
    }

    @Override
    public byte[] pcm() {
        return pcm.clone();
    }

    public int pcmLength() {
        return pcm.length;
    }
}
