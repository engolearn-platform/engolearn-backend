package fit.iuh.engolearn.shared.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class MessageHashes {
    private MessageHashes() {}
    public static String hash(MessageEnvelopeDto envelope) {
        return hash(envelope.messageType() + "\n" + envelope.schemaVersion() + "\n" +
                envelope.aggregateId() + "\n" + envelope.occurredAt() + "\n" + envelope.payload());
    }
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
