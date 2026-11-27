package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;

import java.time.Instant;

public record SpeakingChargeVo(String ownerId, Instant occurredAt, long learnerMs, long successfulWaitMs,
                               long deliveredAudioMs) implements EmbeddedDocument {
    public long seconds() {
        return (learnerMs + successfulWaitMs + deliveredAudioMs + 999) / 1000;
    }
}
